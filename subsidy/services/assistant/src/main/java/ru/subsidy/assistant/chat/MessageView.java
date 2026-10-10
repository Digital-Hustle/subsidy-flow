package ru.subsidy.assistant.chat;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Сообщение диалога.
 *
 * @param messageId идентификатор
 * @param role USER или ASSISTANT
 * @param content текст
 * @param sources источники ответа
 * @param validatorStatus статус сверки чисел (для ASSISTANT)
 * @param validatorDetails неподтверждённые значения
 * @param createdAt время
 */
public record MessageView(UUID messageId, String role, String content, JsonNode sources, String validatorStatus,
                          JsonNode validatorDetails, OffsetDateTime createdAt) {
}
