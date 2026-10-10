package ru.subsidy.docproc.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.docproc.classify.DocType;

/**
 * Пара «счёт — БИК своего банка»: у платёжного поручения два банка, крест-накрест сверять нельзя.
 */
class FieldValidatorTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.600");

    private final ObjectMapper json = new ObjectMapper();
    private final FieldValidator validator = new FieldValidator(json);

    private static DocType paymentOrder() {
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put("payerAccount", "account");
        kinds.put("payeeAccount", "account");
        kinds.put("payerBik", "bik");
        kinds.put("payeeBik", "bik");
        return new DocType("PAYMENT_ORDER", "Платёжное поручение", "", "3", kinds, null);
    }

    private List<FieldValue> normalize(Map<String, String> data) {
        return validator.normalize(paymentOrder(), json.valueToTree(data), json.createObjectNode(), THRESHOLD);
    }

    private static boolean failed(List<FieldValue> fields, String name) {
        return fields.stream().filter(field -> field.name().equals(name))
                .flatMap(field -> field.checks().stream())
                .anyMatch(check -> FieldCheck.FAILED.equals(check.status()));
    }

    @Test
    void accountsCheckedAgainstOwnBank() {
        List<FieldValue> fields = normalize(Map.of(
                "payerAccount", "40702810938000000001", "payerBik", "044525225",
                "payeeAccount", "40702810338000000001", "payeeBik", "045004641"));
        assertThat(failed(fields, "payerAccount")).isFalse();
        assertThat(failed(fields, "payeeAccount")).isFalse();
    }

    @Test
    void wrongKeyIsReported() {
        List<FieldValue> fields = normalize(Map.of(
                "payerAccount", "40702810938000000002", "payerBik", "044525225",
                "payeeAccount", "40702810338000000001", "payeeBik", "045004641"));
        assertThat(failed(fields, "payerAccount")).isTrue();
        assertThat(failed(fields, "payeeAccount")).isFalse();
    }

    @Test
    void innAndOgrnCheckedByFormatOnly() {
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put("inn", "inn");
        kinds.put("ogrn", "ogrn");
        DocType type = new DocType("APPLICATION", "Заявление", "", "4", kinds, null);
        List<FieldValue> good = validator.normalize(type, json.valueToTree(Map.of("inn", "6103 0507 8261",
                "ogrn", "316619600257623")), json.createObjectNode(), THRESHOLD);
        assertThat(failed(good, "inn")).isFalse();
        assertThat(failed(good, "ogrn")).isFalse();
        assertThat(good.getFirst().value().asText()).isEqualTo("610305078261");
        List<FieldValue> bad = validator.normalize(type, json.valueToTree(Map.of("inn", "12345",
                "ogrn", "12345678901234")), json.createObjectNode(), THRESHOLD);
        assertThat(failed(bad, "inn")).isTrue();
        assertThat(failed(bad, "ogrn")).isTrue();
    }
}
