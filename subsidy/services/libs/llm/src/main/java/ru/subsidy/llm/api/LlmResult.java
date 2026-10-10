package ru.subsidy.llm.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ответ модели.
 *
 * @param json      ответ, валидный по схеме
 * @param provider  провайдер
 * @param model     модель
 * @param latencyMs время ответа
 * @param tokensIn  токены запроса (если провайдер сообщает)
 * @param tokensOut токены ответа
 * @param attempts  сколько раз спрашивали модель (перегенерация по схеме)
 */
public record LlmResult(JsonNode json, String provider, String model, long latencyMs, Integer tokensIn,
                        Integer tokensOut, int attempts) {
}
