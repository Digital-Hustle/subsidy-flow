package ru.subsidy.docproc.normalize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.docproc.classify.DocType;

/**
 * Нормализация и проверка полей: деньги, даты, формат ИНН/ОГРН, контрольные суммы КПП/БИК/счёта (код, не модель).
 * ИНН и ОГРН проверяются по формату (10/12 и 13/15 цифр), как требует кейс: контрольная сумма у синтетических
 * документов может не сходиться, а это не повод считать реквизит неверным (ADR-025).
 */
@Component
public class FieldValidator {

    private static final String MONEY = "money";
    private static final String DATE = "date";
    private static final String ACCOUNT = "account";
    private static final String BIK = "bik";
    private static final String CHECKSUM_FAILED = "CHECKSUM_FAILED";
    private static final String FORMAT_INVALID = "FORMAT_INVALID";
    private static final String LOW_CONFIDENCE = "LOW_CONFIDENCE";
    private static final String AMOUNT = "amount";
    private static final Pattern INN_FORMAT = Pattern.compile("\\d{10}|\\d{12}");
    private static final Pattern OGRN_FORMAT = Pattern.compile("\\d{13}|\\d{15}");
    private static final String PRICE = "price";
    private static final int SCALE = 3;
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.700");

    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param json Jackson
     */
    public FieldValidator(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Нормализует ответ модели по схеме вида.
     *
     * @param type вид
     * @param data значения от модели
     * @param confidence уверенность по полям
     * @param reviewThreshold порог ручной проверки
     * @return поля в порядке схемы
     */
    public List<FieldValue> normalize(DocType type, JsonNode data, JsonNode confidence, BigDecimal reviewThreshold) {
        List<FieldValue> result = new ArrayList<>();
        for (Map.Entry<String, String> field : type.kinds().entrySet()) {
            JsonNode raw = data.path(field.getKey());
            if (raw.isMissingNode() || raw.isNull() || raw.isTextual() && raw.asText().isBlank()) {
                continue;
            }
            List<FieldCheck> checks = new ArrayList<>();
            JsonNode value = value(field.getValue(), raw, bikFor(field.getKey(), type, data), checks);
            BigDecimal conf = confidence(confidence.path(field.getKey()));
            if (conf.compareTo(reviewThreshold) < 0) {
                checks.add(new FieldCheck(LOW_CONFIDENCE, FieldCheck.FAILED, "Модель не уверена в значении"));
            }
            result.add(new FieldValue(field.getKey(), value, raw.isTextual() ? raw.asText() : raw.toString(), conf,
                    List.copyOf(checks)));
        }
        return result;
    }

    /**
     * БИК банка того же лица, что и счёт: у платёжного поручения два банка
     * (payerAccount ↔ payerBik, payeeAccount ↔ payeeBik), у остальных видов — единственный bik.
     *
     * @param field имя поля
     * @param type вид документа
     * @param data значения от модели
     * @return БИК или null, если пары нет
     */
    private static String bikFor(String field, DocType type, JsonNode data) {
        int idx = field.indexOf("Account");
        String own = idx <= 0 ? null : field.substring(0, idx) + "Bik";
        if (own != null && BIK.equals(type.kinds().get(own))) {
            return digits(data.path(own));
        }
        return digits(data.path(BIK));
    }

    private JsonNode value(String kind, JsonNode raw, String bik, List<FieldCheck> checks) {
        return switch (kind) {
            case MONEY -> money(raw.asText(), checks);
            case DATE -> date(raw.asText(), checks);
            case "inn" -> format(digits(raw), INN_FORMAT, "ИНН — 10 или 12 цифр", checks);
            case "ogrn" -> format(digits(raw), OGRN_FORMAT, "ОГРН — 13 цифр, ОГРНИП — 15 цифр", checks);
            case "kpp" -> requisite(raw.asText().strip(), Requisites.kpp(raw.asText().strip()), "КПП", checks);
            case BIK -> requisite(digits(raw), Requisites.bik(digits(raw)), "БИК", checks);
            case ACCOUNT -> requisite(digits(raw), bik == null || Requisites.account(digits(raw), bik),
                    "Расчётный счёт", checks);
            case "items" -> items(raw);
            case null, default -> raw;
        };
    }

    private static JsonNode money(String raw, List<FieldCheck> checks) {
        BigDecimal amount = MoneyNormalizer.parse(raw);
        if (amount == null) {
            checks.add(new FieldCheck(FORMAT_INVALID, FieldCheck.FAILED, "Сумма не распознана"));
            return NullNode.getInstance();
        }
        return JsonNodeFactory.instance.textNode(amount.toPlainString());
    }

    private static JsonNode date(String raw, List<FieldCheck> checks) {
        LocalDate date = DateNormalizer.parse(raw);
        if (date == null) {
            checks.add(new FieldCheck(FORMAT_INVALID, FieldCheck.FAILED, "Дата не распознана"));
            return NullNode.getInstance();
        }
        return JsonNodeFactory.instance.textNode(date.toString());
    }

    private static JsonNode requisite(String value, boolean valid, String label, List<FieldCheck> checks) {
        checks.add(valid ? new FieldCheck(CHECKSUM_FAILED, FieldCheck.OK, label + " прошёл проверку")
                : new FieldCheck(CHECKSUM_FAILED, FieldCheck.FAILED, label + " не прошёл проверку контрольной суммы"));
        return JsonNodeFactory.instance.textNode(value == null ? "" : value);
    }

    private static JsonNode format(String value, Pattern pattern, String rule, List<FieldCheck> checks) {
        boolean valid = value != null && pattern.matcher(value).matches();
        checks.add(new FieldCheck(FORMAT_INVALID, valid ? FieldCheck.OK : FieldCheck.FAILED,
                valid ? rule + ": формат верный" : "Неверный формат: " + rule));
        return JsonNodeFactory.instance.textNode(value == null ? "" : value);
    }

    private JsonNode items(JsonNode raw) {
        ArrayNode out = json.createArrayNode();
        for (JsonNode item : raw) {
            ObjectNode row = item.deepCopy();
            for (String key : List.of(AMOUNT, PRICE)) {
                if (item.hasNonNull(key)) {
                    BigDecimal amount = MoneyNormalizer.parse(item.path(key).asText());
                    row.put(key, amount == null ? null : amount.toPlainString());
                }
            }
            out.add(row);
        }
        return out;
    }

    private static BigDecimal confidence(JsonNode node) {
        if (!node.isNumber()) {
            return DEFAULT_CONFIDENCE;
        }
        return BigDecimal.valueOf(Math.clamp(node.asDouble(), 0, 1)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String digits(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        Iterator<Integer> chars = node.asText().chars().iterator();
        chars.forEachRemaining(code -> {
            if (Character.isDigit(code)) {
                out.append((char) code.intValue());
            }
        });
        return out.isEmpty() ? null : out.toString();
    }
}
