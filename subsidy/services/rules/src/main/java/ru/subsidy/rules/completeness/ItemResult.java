package ru.subsidy.rules.completeness;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Пункт чек-листа в протоколе.
 *
 * @param item код пункта
 * @param title название
 * @param kind REQUIRED, ONE_OF, CONDITIONAL
 * @param types подходящие виды
 * @param condition условие по-русски или null
 * @param status OK, MISSING, NOT_APPLICABLE
 * @param documents найденные документы
 * @param applicable применимость для проекта, null — неизвестна
 */
public record ItemResult(String item, String title, String kind, List<String> types, String condition,
                         String status, List<UUID> documents, @Schema(nullable = true) Boolean applicable) {

    /** Есть документ. */
    public static final String OK = "OK";

    /** Нет документа. */
    public static final String MISSING = "MISSING";

    /** Не нужен по условию. */
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
}
