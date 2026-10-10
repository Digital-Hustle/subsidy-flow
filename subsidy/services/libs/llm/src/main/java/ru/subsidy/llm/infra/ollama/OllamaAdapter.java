package ru.subsidy.llm.infra.ollama;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.core.LlmProperties;
import ru.subsidy.llm.core.LlmProvider;
import ru.subsidy.llm.infra.HttpSupport;

/**
 * Локальная модель через Ollama (/api/chat, format = JSON Schema, temperature 0, без «размышлений»).
 */
public class OllamaAdapter implements LlmProvider {

    private static final int NUM_CTX = 8192;
    private static final int SEED = 42;

    private final HttpSupport http;
    private final String base;

    public OllamaAdapter(HttpSupport http, LlmProperties props) {
        this.http = http;
        this.base = props.baseUrl().replaceAll("/+$", "");
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public Completion complete(LlmRequest rq, String model, String feedback) throws IOException {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", rq.system()));
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", rq.user());
        if (rq.vision()) {
            user.put("images", rq.images().stream().map(b -> Base64.getEncoder().encodeToString(b)).toList());
        }
        messages.add(user);
        if (feedback != null) {
            messages.add(Map.of("role", "user", "content", feedback + " Исправь ответ."));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("format", rq.schema());
        body.put("stream", false);
        body.put("think", false);
        body.put("keep_alive", "30m");
        body.put("options", Map.of("temperature", 0, "seed", SEED, "num_ctx", NUM_CTX));
        JsonNode rs = http.postJson(base + "/api/chat", body);
        return new Completion(rs.path("message").path("content").asText(null),
                rs.has("prompt_eval_count") ? rs.get("prompt_eval_count").asInt() : null,
                rs.has("eval_count") ? rs.get("eval_count").asInt() : null);
    }

    @Override
    public void ping() throws IOException {
        http.get(base + "/api/tags");
    }
}
