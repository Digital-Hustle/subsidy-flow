package ru.subsidy.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import ru.subsidy.llm.api.LlmCallRecord;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;
import ru.subsidy.llm.api.ModelInvalidOutputException;
import ru.subsidy.llm.api.ModelUnavailableException;

class DefaultLlmGatewayTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<LlmCallRecord> calls = new ArrayList<>();

    private DefaultLlmGateway gw(LlmProvider p) {
        LlmProperties props = new LlmProperties("ollama", "http://x", "m", null, null, null, 1, 0, null,
                null, null, null, null);
        return new DefaultLlmGateway(p, props, List.of(calls::add), new SimpleMeterRegistry(), json,
                Clock.systemUTC());
    }

    private LlmRequest rq() throws IOException {
        JsonNode schema = json.readTree("""
                {"type":"object","required":["type"],"properties":{"type":{"type":"string"}}}""");
        return new LlmRequest("CLASSIFYING", "d1", "sys", "text", schema, null);
    }

    @Test
    void validAnswerIsReturnedAndLogged() throws IOException {
        LlmResult r = gw(scripted("```json\n{\"type\":\"SNILS\"}\n```")).structured(rq());
        assertThat(r.json().get("type").asText()).isEqualTo("SNILS");
        assertThat(calls).singleElement().extracting(LlmCallRecord::outcome).isEqualTo("OK");
    }

    @Test
    void invalidAnswerIsRegeneratedOnceWithFeedback() throws IOException {
        LlmResult r = gw(scripted("{\"x\":1}", "{\"type\":\"INN\"}")).structured(rq());
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(calls).extracting(LlmCallRecord::outcome).containsExactly("INVALID_OUTPUT", "OK");
    }

    @Test
    void twiceInvalidThrowsInvalidOutput() {
        assertThatThrownBy(() -> gw(scripted("nope", "{\"x\":1}")).structured(rq()))
                .isInstanceOf(ModelInvalidOutputException.class);
    }

    @Test
    void networkErrorBecomesModelUnavailable() {
        LlmProvider down = new LlmProvider() {
            @Override
            public String name() {
                return "ollama";
            }

            @Override
            public Completion complete(LlmRequest rq, String model, String feedback) throws IOException {
                throw new IOException("connection refused");
            }

            @Override
            public void ping() throws IOException {
                throw new IOException("down");
            }
        };
        DefaultLlmGateway g = gw(down);
        assertThatThrownBy(() -> g.structured(rq())).isInstanceOf(ModelUnavailableException.class);
        assertThat(g.status().reachable()).isFalse();
    }

    private static LlmProvider scripted(String... answers) {
        Deque<String> q = new ConcurrentLinkedDeque<>(List.of(answers));
        return new LlmProvider() {
            @Override
            public String name() {
                return "ollama";
            }

            @Override
            public Completion complete(LlmRequest rq, String model, String feedback) {
                return new Completion(q.poll(), 1, 1);
            }

            @Override
            public void ping() {
                // доступен
            }
        };
    }
}
