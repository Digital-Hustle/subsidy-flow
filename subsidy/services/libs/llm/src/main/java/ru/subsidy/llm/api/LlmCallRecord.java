package ru.subsidy.llm.api;

/**
 * Запись журнала обращения к модели (FR-43). Текст документа сюда не попадает.
 *
 * @param documentId  документ
 * @param stage       стадия
 * @param provider    провайдер
 * @param model       модель
 * @param latencyMs   время
 * @param tokensIn    токены запроса
 * @param tokensOut   токены ответа
 * @param schemaValid ответ валиден по схеме
 * @param outcome     OK | INVALID_OUTPUT | UNAVAILABLE
 */
public record LlmCallRecord(String documentId, String stage, String provider, String model, long latencyMs,
                            Integer tokensIn, Integer tokensOut, boolean schemaValid, String outcome) {
}
