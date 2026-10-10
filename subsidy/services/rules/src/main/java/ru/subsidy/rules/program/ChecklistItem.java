package ru.subsidy.rules.program;

import java.util.List;

/**
 * Пункт чек-листа программы.
 *
 * @param item код пункта
 * @param title название для специалиста
 * @param kind REQUIRED, ONE_OF или CONDITIONAL
 * @param types подходящие виды документов
 * @param condition логический параметр программы, при котором пункт нужен (для CONDITIONAL)
 * @param conditionText условие по-русски
 * @param single нужен ровно один документ (иначе дубликат — предупреждение)
 */
public record ChecklistItem(String item, String title, String kind, List<String> types, String condition,
                            String conditionText, boolean single) {

    /** Обязательный пункт. */
    public static final String REQUIRED = "REQUIRED";

    /** Любой из видов. */
    public static final String ONE_OF = "ONE_OF";

    /** Нужен при выполнении условия. */
    public static final String CONDITIONAL = "CONDITIONAL";
}
