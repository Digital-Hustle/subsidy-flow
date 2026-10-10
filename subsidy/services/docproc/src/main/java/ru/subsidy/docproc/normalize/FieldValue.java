package ru.subsidy.docproc.normalize;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Поле документа после нормализации.
 *
 * @param name имя поля схемы
 * @param value нормализованное значение (деньги — строка "12345.67", даты — "YYYY-MM-DD")
 * @param raw как написано в документе
 * @param confidence уверенность
 * @param checks проверки
 */
public record FieldValue(String name, JsonNode value, String raw, BigDecimal confidence, List<FieldCheck> checks) {

    /**
     * Есть ли проваленные проверки.
     *
     * @return true, если хоть одна FAILED
     */
    public boolean failed() {
        return checks.stream().anyMatch(check -> FieldCheck.FAILED.equals(check.status()));
    }
}
