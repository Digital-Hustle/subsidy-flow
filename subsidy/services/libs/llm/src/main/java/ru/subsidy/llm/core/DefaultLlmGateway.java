package ru.subsidy.llm.core;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.subsidy.llm.api.LlmCallListener;
import ru.subsidy.llm.api.LlmCallRecord;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;
import ru.subsidy.llm.api.LlmStatus;
import ru.subsidy.llm.api.ModelInvalidOutputException;
import ru.subsidy.llm.api.ModelUnavailableException;

/**
 * Общая обвязка провайдеров: семафор, ретраи с экспоненциальной паузой, circuit breaker,
 * проверка JSON Schema с одной перегенерацией, метрики и журнал вызовов.
 */
public class DefaultLlmGateway implements LlmGateway {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLlmGateway.class);
    private static final long BACKOFF_MS = 1000;
    private static final int CB_THRESHOLD = 3;
    private static final Duration CB_OPEN = Duration.ofSeconds(15);
    private static final Duration STATUS_TTL = Duration.ofSeconds(10);
    private static final String OK = "OK";

    private final LlmProvider provider;
    private final LlmProperties props;
    private final List<LlmCallListener> listeners;
    private final MeterRegistry meters;
    private final ObjectMapper json;
    private final Clock clock;
    private final Semaphore permits;
    private final JsonSchemaFactory schemas = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private int failures;
    private Instant openUntil = Instant.EPOCH;
    private LlmStatus cachedStatus;

    public DefaultLlmGateway(LlmProvider provider, LlmProperties props, List<LlmCallListener> listeners,
                             MeterRegistry meters, ObjectMapper json, Clock clock) {
        this.provider = provider;
        this.props = props;
        this.listeners = listeners;
        this.meters = meters;
        this.json = json;
        this.clock = clock;
        this.permits = new Semaphore(props.maxConcurrency(), true);
    }

    @Override
    public LlmResult structured(LlmRequest rq) {
        String model = rq.vision() ? props.visionModel() : props.model();
        JsonSchema schema = schemas.getSchema(rq.schema());
        String feedback = null;
        long started = System.nanoTime();
        int attempts = 0;
        Integer tin = null;
        Integer tout = null;
        for (int gen = 0; gen < 2; gen++) {
            attempts++;
            LlmProvider.Completion c = callWithRetry(rq, model, feedback);
            tin = c.tokensIn();
            tout = c.tokensOut();
            JsonNode node = parse(c.text());
            Set<ValidationMessage> errors = node == null ? Set.of() : schema.validate(node);
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (node != null && errors.isEmpty()) {
                record(rq, model, ms, tin, tout, true, OK);
                return new LlmResult(node, provider.name(), model, ms, tin, tout, attempts);
            }
            feedback = node == null ? "Ответ не является JSON-объектом." : "Ответ не прошёл проверку схемы: " + errors;
            record(rq, model, ms, tin, tout, false, "INVALID_OUTPUT");
            LOG.info("llm schema invalid stage={} doc={} errors={}", rq.stage(), rq.documentId(), errors.size());
        }
        meters.counter("llm_schema_invalid_total", "provider", provider.name(), "model", model).increment();
        throw new ModelInvalidOutputException(feedback);
    }

    private LlmProvider.Completion callWithRetry(LlmRequest rq, String model, String feedback) {
        if (clock.instant().isBefore(openUntilSafe())) {
            record(rq, model, 0, null, null, false, "UNAVAILABLE");
            throw new ModelUnavailableException("circuit breaker open", null);
        }
        IOException last = null;
        for (int i = 0; i <= props.retries(); i++) {
            if (i > 0) {
                sleep(BACKOFF_MS << (i - 1));
            }
            Timer.Sample sample = Timer.start(meters);
            try {
                acquire();
                try {
                    LlmProvider.Completion c = provider.complete(rq, model, feedback);
                    sample.stop(timer(model, OK));
                    onSuccess();
                    return c;
                } finally {
                    permits.release();
                }
            } catch (IOException e) {
                sample.stop(timer(model, "ERROR"));
                last = e;
                LOG.warn("llm call failed provider={} attempt={} : {}", provider.name(), i + 1, e.getMessage());
            }
        }
        onFailure();
        record(rq, model, 0, null, null, false, "UNAVAILABLE");
        throw new ModelUnavailableException(last == null ? "unavailable" : last.getMessage(), last);
    }

    private Timer timer(String model, String outcome) {
        return Timer.builder("llm_request_seconds").tag("provider", provider.name()).tag("model", model)
                .tag("outcome", outcome).register(meters);
    }

    private void acquire() throws IOException {
        try {
            if (!permits.tryAcquire(props.timeout().toMillis() * 2, TimeUnit.MILLISECONDS)) {
                throw new IOException("llm concurrency wait timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private synchronized Instant openUntilSafe() {
        return openUntil;
    }

    private synchronized void onSuccess() {
        failures = 0;
    }

    private synchronized void onFailure() {
        failures++;
        if (failures >= CB_THRESHOLD) {
            openUntil = clock.instant().plus(CB_OPEN);
        }
    }

    private JsonNode parse(String text) {
        if (text == null) {
            return null;
        }
        int a = text.indexOf('{');
        int b = text.lastIndexOf('}');
        if (a < 0 || b <= a) {
            return null;
        }
        try {
            JsonNode n = json.readTree(text.substring(a, b + 1));
            return n != null && n.isObject() ? n : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void record(LlmRequest rq, String model, long ms, Integer tin, Integer tout, boolean valid,
                        String outcome) {
        LlmCallRecord r = new LlmCallRecord(rq.documentId(), rq.stage(), provider.name(), model, ms, tin, tout,
                valid, outcome);
        for (LlmCallListener l : listeners) {
            try {
                l.onCall(r);
            } catch (RuntimeException e) {
                LOG.warn("llm call listener failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized LlmStatus status() {
        Instant now = clock.instant();
        if (cachedStatus != null && cachedStatus.checkedAt().plus(STATUS_TTL).isAfter(now)) {
            return cachedStatus;
        }
        String error = null;
        try {
            provider.ping();
        } catch (IOException e) {
            error = e.getMessage();
        }
        cachedStatus = new LlmStatus(provider.name(), props.model(), props.visionModel(), error == null, now, error);
        return cachedStatus;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
