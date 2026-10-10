package ru.subsidy.assistant.chat;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Вызов инструмента в рамках вопроса.
 *
 * @param tool имя
 * @param argument аргумент
 * @param result результат
 */
public record ToolCall(String tool, String argument, JsonNode result) {
}
