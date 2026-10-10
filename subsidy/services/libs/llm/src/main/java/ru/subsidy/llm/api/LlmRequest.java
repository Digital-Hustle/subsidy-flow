package ru.subsidy.llm.api;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Запрос к модели.
 *
 * @param stage      стадия конвейера (для журнала)
 * @param documentId документ (для журнала)
 * @param system     системный промпт
 * @param user       пользовательское сообщение (текст документа)
 * @param schema     JSON Schema ответа
 * @param images     PNG/JPEG страниц для vision-пути (пусто — текстовая модель)
 */
public record LlmRequest(String stage, String documentId, String system, String user, JsonNode schema,
                         List<byte[]> images) {

    public LlmRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    /**
     * Есть ли картинки.
     *
     * @return true для vision-запроса
     */
    public boolean vision() {
        return !images.isEmpty();
    }
}
