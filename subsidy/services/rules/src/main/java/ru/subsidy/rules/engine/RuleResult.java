package ru.subsidy.rules.engine;

import java.util.List;

/**
 * Результат одного правила с объяснением.
 *
 * @param rule код правила
 * @param status статус
 * @param message текст специалисту
 * @param evidence ссылки на поля
 * @param expected ожидалось
 * @param actual фактически
 */
public record RuleResult(String rule, String status, String message, List<Evidence> evidence, String expected,
                         String actual) {

    /**
     * Выполнено.
     *
     * @param rule код
     * @param message текст
     * @param evidence ссылки
     * @return результат
     */
    public static RuleResult passed(String rule, String message, List<Evidence> evidence) {
        return new RuleResult(rule, RuleStatus.PASSED, message, evidence, null, null);
    }

    /**
     * Нарушено.
     *
     * @param rule код
     * @param message текст
     * @param evidence ссылки
     * @param expected ожидалось
     * @param actual фактически
     * @return результат
     */
    public static RuleResult failed(String rule, String message, List<Evidence> evidence, String expected,
                                    String actual) {
        return new RuleResult(rule, RuleStatus.FAILED, message, evidence, expected, actual);
    }

    /**
     * Предупреждение.
     *
     * @param rule код
     * @param message текст
     * @param evidence ссылки
     * @return результат
     */
    public static RuleResult warning(String rule, String message, List<Evidence> evidence) {
        return new RuleResult(rule, RuleStatus.WARNING, message, evidence, null, null);
    }

    /**
     * Нет данных.
     *
     * @param rule код
     * @param message что не найдено
     * @return результат
     */
    public static RuleResult missing(String rule, String message) {
        return new RuleResult(rule, RuleStatus.DATA_MISSING, message, List.of(), null, null);
    }

    /**
     * Не применяется.
     *
     * @param rule код
     * @param message почему
     * @return результат
     */
    public static RuleResult notApplicable(String rule, String message) {
        return new RuleResult(rule, RuleStatus.NOT_APPLICABLE, message, List.of(), null, null);
    }

    /**
     * Нарушено или предупреждение — по флагу.
     *
     * @param rule код
     * @param passedCheck выполнено ли
     * @param passText текст при успехе
     * @param failText текст при нарушении
     * @param evidence ссылки
     * @return PASSED или FAILED
     */
    public static RuleResult check(String rule, boolean passedCheck, String passText, String failText,
                                   List<Evidence> evidence) {
        return passedCheck ? passed(rule, passText, evidence) : failed(rule, failText, evidence, null, null);
    }
}
