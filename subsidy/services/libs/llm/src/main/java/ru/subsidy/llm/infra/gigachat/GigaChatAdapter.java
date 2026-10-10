package ru.subsidy.llm.infra.gigachat;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.core.LlmProperties;
import ru.subsidy.llm.core.LlmProvider;
import ru.subsidy.llm.infra.HttpSupport;

/**
 * GigaChat API: OAuth по ключу авторизации, затем /chat/completions; JSON — по инструкции в промпте.
 */
public class GigaChatAdapter implements LlmProvider {

    private static final String DEFAULT_AUTH = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String DEFAULT_BASE = "https://gigachat.devices.sberbank.ru/api/v1";
    private static final long SKEW_MS = 60_000;
    private static final String CONTENT = "content";
    private static final String ROLE = "role";

    private final HttpSupport http;
    private final LlmProperties props;
    private final String base;
    private final Clock clock;
    private String token;
    private Instant tokenUntil = Instant.EPOCH;

    public GigaChatAdapter(HttpSupport http, LlmProperties props, Clock clock) {
        this.http = http;
        this.props = props;
        this.clock = clock;
        String b = props.baseUrl();
        this.base = b == null || b.isBlank() || b.contains("ollama") ? DEFAULT_BASE : b.replaceAll("/+$", "");
    }

    @Override
    public String name() {
        return "gigachat";
    }

    @Override
    public Completion complete(LlmRequest rq, String model, String feedback) throws IOException {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, "system", CONTENT, rq.system() + "\n\n" + LlmProvider.schemaHint(rq.schema())));
        messages.add(Map.of(ROLE, "user", CONTENT, rq.user()));
        if (feedback != null) {
            messages.add(Map.of(ROLE, "user", CONTENT, feedback + " Исправь ответ."));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.01);
        JsonNode rs = http.postJson(base + "/chat/completions", body, "Authorization", "Bearer " + token());
        JsonNode usage = rs.path("usage");
        return new Completion(rs.path("choices").path(0).path("message").path(CONTENT).asText(null),
                usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
    }

    private synchronized String token() throws IOException {
        if (token != null && clock.instant().isBefore(tokenUntil)) {
            return token;
        }
        String url = props.gigachatAuthUrl() == null || props.gigachatAuthUrl().isBlank()
                ? DEFAULT_AUTH : props.gigachatAuthUrl();
        String scope = props.gigachatScope() == null ? "GIGACHAT_API_PERS" : props.gigachatScope();
        JsonNode rs = http.postForm(url, "scope=" + scope, "Authorization", "Basic " + props.gigachatAuthKey(),
                "RqUID", UUID.randomUUID().toString(), "Accept", "application/json");
        token = rs.path("access_token").asText(null);
        if (token == null) {
            throw new IOException("GigaChat OAuth: no access_token");
        }
        long exp = rs.path("expires_at").asLong(clock.millis() + SKEW_MS * 2);
        tokenUntil = Instant.ofEpochMilli(exp - SKEW_MS);
        return token;
    }

    @Override
    public void ping() throws IOException {
        http.get(base + "/models", "Authorization", "Bearer " + token());
    }
}
