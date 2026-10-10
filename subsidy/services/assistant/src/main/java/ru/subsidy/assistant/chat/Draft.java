package ru.subsidy.assistant.chat;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.validate.Validation;

/**
 * Ответ модели до сохранения.
 *
 * @param answer текст
 * @param sources источники
 * @param validation сверка чисел
 * @param model модель
 */
public record Draft(String answer, JsonNode sources, Validation validation, String model) {
}
