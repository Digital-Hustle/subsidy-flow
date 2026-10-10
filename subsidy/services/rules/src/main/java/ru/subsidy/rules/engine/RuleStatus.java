package ru.subsidy.rules.engine;

/**
 * Статусы результата правила (RULES-CATALOG).
 */
public final class RuleStatus {

    /** Выполнено. */
    public static final String PASSED = "PASSED";

    /** Нарушено. */
    public static final String FAILED = "FAILED";

    /** Предупреждение — решает специалист. */
    public static final String WARNING = "WARNING";

    /** Нет данных для проверки (не PASSED). */
    public static final String DATA_MISSING = "DATA_MISSING";

    /** Не применяется по условию. */
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    /** Правило упало — пакет на ручную проверку. */
    public static final String ERROR = "ERROR";

    private RuleStatus() {
    }
}
