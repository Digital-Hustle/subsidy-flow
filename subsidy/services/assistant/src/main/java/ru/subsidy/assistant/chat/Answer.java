package ru.subsidy.assistant.chat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.validate.Validation;

/**
 * Ответ помощника.
 *
 * @param messageId сообщение
 * @param conversationId диалог
 * @param answer текст
 * @param sources источники [{documentId, field, rule}]
 * @param tools вызванные инструменты (имена)
 * @param validator сверка чисел
 * @param model модель
 * @param createdAt время
 */
public record Answer(UUID messageId, UUID conversationId, String answer, JsonNode sources, List<String> tools,
                     Validation validator, String model, OffsetDateTime createdAt) {

    /**
     * Копия списка.
     *
     * @param messageId сообщение
     * @param conversationId диалог
     * @param answer текст
     * @param sources источники
     * @param tools инструменты
     * @param validator сверка
     * @param model модель
     * @param createdAt время
     */
    public Answer {
        tools = List.copyOf(tools);
    }
}
