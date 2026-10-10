package ru.subsidy.llm.infra.openai;

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
 * Любой OpenAI-совместимый API (/chat/completions): DeepSeek, Qwen, GLM, vLLM, Ollama /v1.
 */
public class OpenAiCompatAdapter implements LlmProvider {

    private static final String CONTENT = "content";
    private static final String ROLE = "role";

    private final HttpSupport http;
    private final String base;
    private final String apiKey;
    private final String jsonMode;

    public OpenAiCompatAdapter(HttpSupport http, LlmProperties props) {
        this.http = http;
        this.base = props.baseUrl().replaceAll("/+$", "");
        this.apiKey = props.apiKey() == null ? "" : props.apiKey();
        this.jsonMode = props.jsonMode();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public Completion complete(LlmRequest rq, String model, String feedback) throws IOException {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, "system", CONTENT, rq.system() + "\n\n" + LlmProvider.schemaHint(rq.schema())));
        if (rq.vision()) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", rq.user()));
            for (byte[] img : rq.images()) {
                parts.add(Map.of("type", "image_url", "image_url",
                        Map.of("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(img))));
            }
            messages.add(Map.of(ROLE, "user", CONTENT, parts));
        } else {
            messages.add(Map.of(ROLE, "user", CONTENT, rq.user()));
        }
        if (feedback != null) {
            messages.add(Map.of(ROLE, "user", CONTENT, feedback + " Исправь ответ."));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0);
        if ("json_schema".equals(jsonMode)) {
            body.put("response_format", Map.of("type", "json_schema",
                    "json_schema", Map.of("name", "result", "schema", rq.schema())));
        } else if ("json_object".equals(jsonMode)) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        JsonNode rs = http.postJson(base + "/chat/completions", body, "Authorization", "Bearer " + apiKey);
        JsonNode usage = rs.path("usage");
        return new Completion(rs.path("choices").path(0).path("message").path(CONTENT).asText(null),
                usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
    }

    @Override
    public void ping() throws IOException {
        http.get(base + "/models", "Authorization", "Bearer " + apiKey);
    }
}
