package ru.subsidy.rules.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Правка значения поля специалистом (данные, не вердикт).
 *
 * @param documentId документ
 * @param field имя поля
 * @param value новое значение (пусто — поле не заполнено)
 * @param reason причина
 */
public record FieldRq(@NotNull UUID documentId, @NotBlank @Size(max = FieldRq.MAX_NAME) String field,
                      @Size(max = FieldRq.MAX_VALUE) String value, @Size(max = FieldRq.MAX_VALUE) String reason) {

    /** Длина имени поля. */
    public static final int MAX_NAME = 100;

    /** Длина значения и причины. */
    public static final int MAX_VALUE = 2000;
}
