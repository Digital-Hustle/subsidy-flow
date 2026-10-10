package ru.subsidy.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Вопрос по пакету.
 *
 * @param question текст вопроса
 */
public record AskRq(@NotBlank @Size(max = AskRq.MAX_LENGTH) String question) {

    /** Максимальная длина вопроса. */
    public static final int MAX_LENGTH = 1000;
}
