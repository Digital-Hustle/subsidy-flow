package ru.subsidy.assistant.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.chat.Answer;
import ru.subsidy.assistant.validate.Validation;

/**
 * Ответ помощника.
 *
 * @param messageId сообщение
 * @param answer текст
 * @param sources источники [{documentId, field?, rule?}]
 * @param tools вызванные инструменты
 * @param validator сверка чисел {status: OK|NUMBERS_MISMATCH, details}
 * @param model модель
 * @param createdAt время
 */
public record AskRs(UUID messageId, String answer, JsonNode sources, List<String> tools, ValidatorRs validator,
                    String model, OffsetDateTime createdAt) {

    /**
     * Из доменного ответа.
     *
     * @param answer ответ
     * @return DTO
     */
    public static AskRs of(Answer answer) {
        Validation validation = answer.validator();
        return new AskRs(answer.messageId(), answer.answer(), answer.sources(), answer.tools(),
                new ValidatorRs(validation.status(), validation.unknown()), answer.model(), answer.createdAt());
    }

    /**
     * Статус валидатора.
     *
     * @param status OK или NUMBERS_MISMATCH
     * @param details числа/даты ответа, которых нет в данных
     */
    public record ValidatorRs(String status, List<String> details) {
    }
}
