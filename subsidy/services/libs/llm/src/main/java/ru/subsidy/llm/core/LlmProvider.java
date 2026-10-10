package ru.subsidy.llm.core;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.llm.api.LlmRequest;

/**
 * Адаптер провайдера: один «сырой» вызов. Ретраи, семафор, CB, схема и журнал — в {@link DefaultLlmGateway}.
 */
public interface LlmProvider {

    /**
     * Имя провайдера.
     *
     * @return gigachat | openai | ollama
     */
    String name();

    /**
     * Один вызов модели.
     *
     * @param rq       запрос
     * @param model    модель
     * @param feedback текст ошибки валидации прошлого ответа (null при первом вызове)
     * @return ответ
     * @throws IOException сеть/таймаут/5xx — повод для повтора и WAITING_MODEL
     */
    Completion complete(LlmRequest rq, String model, String feedback) throws IOException;

    /**
     * Проверка доступности (лёгкий запрос).
     *
     * @throws IOException недоступен
     */
    void ping() throws IOException;

    /**
     * Ответ провайдера.
     *
     * @param text      текст ответа (ожидается JSON)
     * @param tokensIn  токены запроса
     * @param tokensOut токены ответа
     */
    record Completion(String text, Integer tokensIn, Integer tokensOut) {
    }

    /**
     * Схема для подсказки модели в промпте (если провайдер не поддерживает structured output).
     *
     * @param schema схема
     * @return строка
     */
    static String schemaHint(JsonNode schema) {
        return "Ответь строго одним JSON-объектом без пояснений и без markdown, по JSON Schema:\n" + schema;
    }
}
