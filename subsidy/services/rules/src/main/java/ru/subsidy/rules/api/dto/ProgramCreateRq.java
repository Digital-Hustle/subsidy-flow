package ru.subsidy.rules.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Новая программа.
 *
 * @param code код: латиница в верхнем регистре, цифры и «_», 2–32 символа, начинается с буквы
 * @param name название
 * @param params параметры (объект: description, deadline, rules)
 * @param checklist чек-лист (массив пунктов)
 */
public record ProgramCreateRq(@NotBlank @Pattern(regexp = CODE_PATTERN) String code,
                              @NotBlank @Size(max = ProgramRq.MAX_NAME) String name,
                              @NotNull JsonNode params, @NotNull JsonNode checklist) {

    /** Формат кода программы. */
    public static final String CODE_PATTERN = "[A-Z][A-Z0-9_]{1,31}";
}
