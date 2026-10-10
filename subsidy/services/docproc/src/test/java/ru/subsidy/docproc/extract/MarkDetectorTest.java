package ru.subsidy.docproc.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;
import ru.subsidy.llm.api.LlmStatus;
import ru.subsidy.llm.api.ModelInvalidOutputException;

/**
 * Визуальная проверка не мешает разбору: без изображения модель не вызывается, сбой модели → UNDETERMINED.
 */
class MarkDetectorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DocType APPLICATION = new DocType("APPLICATION", "Заявление", null, "1", Map.of(), null);

    @Test
    void noImagesNoModelCall() {
        AtomicInteger calls = new AtomicInteger();
        MarkDetector detector = new MarkDetector(gateway(calls, false), JSON);
        DocumentMarks marks = detector.detect("doc", APPLICATION, List.of());
        assertThat(calls).hasValue(0);
        assertThat(marks.signature().status()).isEqualTo(MarkState.UNDETERMINED);
    }

    @Test
    void invalidModelOutputIsUndetermined() {
        AtomicInteger calls = new AtomicInteger();
        MarkDetector detector = new MarkDetector(gateway(calls, true), JSON);
        DocumentMarks marks = detector.detect("doc", APPLICATION, List.of(new byte[] {1}));
        assertThat(calls).hasValue(1);
        assertThat(marks.signature().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.stamp().status()).isEqualTo(MarkState.UNDETERMINED);
    }

    @Test
    void onlySignedTypes() {
        assertThat(MarkDetector.applies("APPLICATION")).isTrue();
        assertThat(MarkDetector.applies("GUARANTEE_LETTER")).isTrue();
        assertThat(MarkDetector.applies("BANK_STATEMENT")).isFalse();
    }

    private static LlmGateway gateway(AtomicInteger calls, boolean broken) {
        return new LlmGateway() {
            @Override
            public LlmResult structured(LlmRequest rq) {
                calls.incrementAndGet();
                if (broken) {
                    throw new ModelInvalidOutputException("bad");
                }
                return new LlmResult(JSON.createObjectNode(), "test", "vision", 1, null, null, 1);
            }

            @Override
            public LlmStatus status() {
                return null;
            }
        };
    }
}
