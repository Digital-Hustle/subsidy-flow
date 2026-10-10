package ru.subsidy.rules.facts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.money.Money;

/**
 * Документ пакета с полями для правил.
 *
 * @param documentId документ
 * @param fileId файл
 * @param type вид
 * @param typeConfidence уверенность вида
 * @param stage стадия
 * @param reasonCode причина (TYPE_UNKNOWN, TYPE_OTHER, FIELDS_TO_CHECK)
 * @param fromPage первая страница
 * @param toPage последняя страница
 * @param fields поля по имени
 */
public record DocFacts(UUID documentId, UUID fileId, String type, BigDecimal typeConfidence, String stage,
                       String reasonCode, int fromPage, int toPage, Map<String, FieldFact> fields) {

    /**
     * Поле, если задано.
     *
     * @param name имя
     * @return поле
     */
    public Optional<FieldFact> field(String name) {
        return Optional.ofNullable(fields.get(name)).filter(FieldFact::present);
    }

    /**
     * Текст поля.
     *
     * @param name имя
     * @return строка или пусто
     */
    public Optional<String> text(String name) {
        return field(name).map(fact -> fact.value().asText().strip());
    }

    /**
     * Сумма поля.
     *
     * @param name имя
     * @return сумма или пусто
     */
    public Optional<BigDecimal> money(String name) {
        return text(name).map(Money::parse);
    }

    /**
     * Дата поля (YYYY-MM-DD).
     *
     * @param name имя
     * @return дата или пусто
     */
    public Optional<LocalDate> date(String name) {
        return text(name).flatMap(DocFacts::parseDate);
    }

    /**
     * Логическое поле.
     *
     * @param name имя
     * @return значение или пусто
     */
    public Optional<Boolean> bool(String name) {
        return field(name).map(FieldFact::value).flatMap(DocFacts::parseBool);
    }

    /**
     * Позиции таблицы (items).
     *
     * @param name имя
     * @return строки или пустой список
     */
    public List<JsonNode> rows(String name) {
        return field(name).map(FieldFact::value).filter(JsonNode::isArray)
                .map(node -> {
                    List<JsonNode> out = new ArrayList<>();
                    node.forEach(out::add);
                    return out;
                }).orElse(List.of());
    }

    private static Optional<LocalDate> parseDate(String text) {
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException broken) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> parseBool(JsonNode node) {
        Optional<Boolean> result = Optional.empty();
        if (node.isBoolean()) {
            result = Optional.of(node.asBoolean());
        } else if (node.isTextual()) {
            String text = node.asText().strip().toLowerCase(Locale.ROOT);
            if ("true".equals(text) || "да".equals(text)) {
                result = Optional.of(Boolean.TRUE);
            } else if ("false".equals(text) || "нет".equals(text)) {
                result = Optional.of(Boolean.FALSE);
            }
        }
        return result;
    }
}
