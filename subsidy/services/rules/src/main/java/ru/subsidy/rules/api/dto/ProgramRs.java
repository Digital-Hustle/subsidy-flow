package ru.subsidy.rules.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.program.ChecklistItem;
import ru.subsidy.rules.program.Program;

/**
 * Программа: параметры и чек-лист текущей версии.
 *
 * @param code код
 * @param version версия
 * @param name название
 * @param demo демо
 * @param params параметры
 * @param checklist чек-лист
 * @param updatedBy автор версии
 * @param updatedAt время версии
 */
public record ProgramRs(String code, int version, String name, boolean demo, JsonNode params,
                        List<ChecklistItem> checklist, String updatedBy, OffsetDateTime updatedAt) {

    /**
     * Из доменной модели.
     *
     * @param program программа
     * @return ответ
     */
    public static ProgramRs of(Program program) {
        return new ProgramRs(program.code(), program.version(), program.name(), program.demo(),
                program.params().raw(), program.checklist(), program.createdBy(), program.createdAt());
    }
}
