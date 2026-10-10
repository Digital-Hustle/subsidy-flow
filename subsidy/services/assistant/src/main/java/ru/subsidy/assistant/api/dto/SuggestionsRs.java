package ru.subsidy.assistant.api.dto;

import java.util.List;

/**
 * Подсказки вопросов.
 *
 * @param items вопросы
 */
public record SuggestionsRs(List<String> items) {
}
