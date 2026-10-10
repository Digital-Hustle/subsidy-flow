package ru.subsidy.llm.core;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки слоя модели (docs/LLM.md, infra/.env).
 *
 * @param provider        gigachat | openai | ollama
 * @param baseUrl         адрес провайдера
 * @param model           текстовая модель
 * @param visionModel     vision-модель
 * @param apiKey          ключ облака
 * @param timeout         таймаут одного вызова
 * @param maxConcurrency  одновременных вызовов
 * @param retries         повторов при сетевой ошибке
 * @param jsonMode        для openai: json_schema | json_object | none (без response_format)
 * @param gigachatAuthKey ключ авторизации GigaChat (Basic)
 * @param gigachatScope   scope GigaChat
 * @param gigachatAuthUrl адрес OAuth GigaChat
 * @param caCertPath      дополнительный корневой сертификат (НУЦ Минцифры для GigaChat)
 */
@ConfigurationProperties("app.llm")
public record LlmProperties(String provider, String baseUrl, String model, String visionModel, String apiKey,
                            Duration timeout, Integer maxConcurrency, Integer retries, String jsonMode,
                            String gigachatAuthKey, String gigachatScope, String gigachatAuthUrl,
                            String caCertPath) {

    private static final int DEFAULT_TIMEOUT_S = 60;
    private static final int DEFAULT_RETRIES = 2;

    public LlmProperties {
        provider = provider == null || provider.isBlank() ? "ollama" : provider.trim().toLowerCase();
        timeout = timeout == null ? Duration.ofSeconds(DEFAULT_TIMEOUT_S) : timeout;
        maxConcurrency = maxConcurrency == null || maxConcurrency < 1 ? 2 : maxConcurrency;
        retries = retries == null ? DEFAULT_RETRIES : retries;
        jsonMode = jsonMode == null || jsonMode.isBlank() ? "json_schema" : jsonMode;
        visionModel = visionModel == null || visionModel.isBlank() ? model : visionModel;
    }
}
