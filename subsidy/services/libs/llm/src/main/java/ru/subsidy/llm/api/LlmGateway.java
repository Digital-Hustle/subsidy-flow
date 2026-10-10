package ru.subsidy.llm.api;

/**
 * Единственная точка обращения к модели (Д6). Провайдер выбирается настройкой app.llm.provider.
 * Реализация гарантирует: ответ валиден по JSON Schema или исключение.
 */
public interface LlmGateway {

    /**
     * Структурированный ответ модели по JSON Schema.
     *
     * @param rq запрос
     * @return ответ, валидный по схеме
     * @throws ModelUnavailableException    модель недоступна (сеть, таймаут, circuit breaker)
     * @throws ModelInvalidOutputException  ответ не по схеме даже после перегенерации
     */
    LlmResult structured(LlmRequest rq);

    /**
     * Доступность провайдера (кэшируется на несколько секунд).
     *
     * @return статус
     */
    LlmStatus status();
}
