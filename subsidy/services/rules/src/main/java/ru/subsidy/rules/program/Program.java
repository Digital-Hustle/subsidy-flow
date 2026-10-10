package ru.subsidy.rules.program;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Версия программы субсидирования: параметры и чек-лист — данные, не код.
 *
 * @param code код
 * @param version версия
 * @param name название
 * @param demo демо-программа
 * @param params параметры
 * @param checklist чек-лист
 * @param createdBy автор версии
 * @param createdAt время версии
 */
public record Program(String code, int version, String name, boolean demo, ProgramParams params,
                      List<ChecklistItem> checklist, String createdBy, OffsetDateTime createdAt) {
}
