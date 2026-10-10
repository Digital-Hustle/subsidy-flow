package ru.subsidy.rules.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Новая версия программы.
 *
 * @param name название (пусто — прежнее)
 * @param params параметры (объект)
 * @param checklist чек-лист (массив пунктов)
 */
public record ProgramRq(@Size(max = ProgramRq.MAX_NAME) String name, @NotNull JsonNode params,
                        @NotNull JsonNode checklist) {

    /** Длина названия. */
    public static final int MAX_NAME = 300;
}
