package ru.subsidy.rules.engine.declarative;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;

/**
 * Правило программы как данные: код, название, операция, серьёзность и параметры операции.
 * Хранится в {@code programs.params.rules}, меняется администратором без правки кода (ADR-024).
 *
 * @param code код правила (в протоколе и статистике)
 * @param title название для специалиста
 * @param op операция ({@link DeclarativeOp#name()})
 * @param severity FAILED или WARNING — статус при нарушении
 * @param enabled включено ли правило
 * @param params исходный JSON правила (параметры операции)
 */
public record RuleSpec(String code, String title, String op, String severity, boolean enabled, JsonNode params) {

    /**
     * Нарушение со статусом по серьёзности правила.
     *
     * @param message что не так
     * @param evidence где
     * @param expected ожидалось
     * @param actual фактически
     * @return FAILED или WARNING
     */
    public RuleResult fail(String message, List<Evidence> evidence, String expected, String actual) {
        return new RuleResult(code, severity, message, evidence, expected, actual);
    }

    /**
     * Выполнено.
     *
     * @param message текст
     * @param evidence где
     * @return PASSED
     */
    public RuleResult pass(String message, List<Evidence> evidence) {
        return RuleResult.passed(code, message, evidence);
    }

    /**
     * Предупреждение независимо от серьёзности (например, срок скоро истечёт).
     *
     * @param message текст
     * @param evidence где
     * @return WARNING
     */
    public RuleResult warn(String message, List<Evidence> evidence) {
        return new RuleResult(code, RuleStatus.WARNING, message, evidence, null, null);
    }

    /**
     * Нет данных для проверки (не PASSED).
     *
     * @param message чего не хватает
     * @return DATA_MISSING
     */
    public RuleResult missing(String message) {
        return RuleResult.missing(code, message);
    }

    /**
     * Не применяется.
     *
     * @param message почему
     * @return NOT_APPLICABLE
     */
    public RuleResult notApplicable(String message) {
        return RuleResult.notApplicable(code, message);
    }
}
