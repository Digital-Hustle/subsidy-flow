package ru.subsidy.llm.core;

import java.time.Clock;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import ru.subsidy.llm.api.LlmCallListener;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.infra.HttpSupport;
import ru.subsidy.llm.infra.gigachat.GigaChatAdapter;
import ru.subsidy.llm.infra.ollama.OllamaAdapter;
import ru.subsidy.llm.infra.openai.OpenAiCompatAdapter;

/**
 * Выбор провайдера по app.llm.provider; потребитель видит только {@link LlmGateway}.
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmAutoConfiguration {

    /** Провайдер модели. */
    @Bean
    public LlmProvider llmProvider(LlmProperties props, ObjectMapper json, ObjectProvider<Clock> clock) {
        HttpSupport http = new HttpSupport(json, props.timeout(), props.caCertPath());
        return switch (props.provider()) {
            case "openai" -> new OpenAiCompatAdapter(http, props);
            case "gigachat" -> new GigaChatAdapter(http, props, clock.getIfAvailable(Clock::systemUTC));
            case "ollama" -> new OllamaAdapter(http, props);
            default -> throw new IllegalStateException("unknown LLM_PROVIDER: " + props.provider());
        };
    }

    /** Шлюз модели. */
    @Bean
    public LlmGateway llmGateway(LlmProvider provider, LlmProperties props, List<LlmCallListener> listeners,
                                 MeterRegistry meters, ObjectMapper json, ObjectProvider<Clock> clock) {
        return new DefaultLlmGateway(provider, props, listeners, meters, json, clock.getIfAvailable(Clock::systemUTC));
    }
}
