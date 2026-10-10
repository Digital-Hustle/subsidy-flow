package ru.subsidy.assistant.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.chat.MessageView;

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
public record MessageRs(UUID messageId, String role, String content, JsonNode sources, String validatorStatus,
                        JsonNode validatorDetails, OffsetDateTime createdAt) {

    /**
     * Из доменного сообщения.
     *
     * @param view сообщение
     * @return DTO
     */
    public static MessageRs of(MessageView view) {
        return new MessageRs(view.messageId(), view.role(), view.content(), view.sources(), view.validatorStatus(),
                view.validatorDetails(), view.createdAt());
    }
}
