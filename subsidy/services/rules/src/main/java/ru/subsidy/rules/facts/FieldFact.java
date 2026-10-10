package ru.subsidy.rules.facts;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Значение поля документа для правил: правка специалиста важнее значения модели.
 *
 * @param name имя поля
 * @param value значение (деньги и даты — строки)
 * @param confidence уверенность модели
 * @param source MODEL или SPECIALIST
 * @param flagged есть проваленная проверка формата (без правки)
 */
public record FieldFact(String name, JsonNode value, BigDecimal confidence, String source, boolean flagged) {

    /** Значение от модели. */
    public static final String MODEL = "MODEL";

    /** Правка специалиста. */
    public static final String SPECIALIST = "SPECIALIST";

    /**
     * Есть ли непустое значение.
     *
     * @return true, если значение задано
     */
    public boolean present() {
        return value != null && !value.isNull() && !(value.isTextual() && value.asText().isBlank());
    }
}
