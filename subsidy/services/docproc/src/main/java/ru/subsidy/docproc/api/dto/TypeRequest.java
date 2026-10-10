package ru.subsidy.docproc.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Смена вида документа.
 *
 * @param type новый вид
 * @param reason причина
 */
public record TypeRequest(@NotBlank String type, @Size(max = TypeRequest.REASON_MAX) String reason) {

    /** Максимальная длина причины. */
    public static final int REASON_MAX = 500;
}
