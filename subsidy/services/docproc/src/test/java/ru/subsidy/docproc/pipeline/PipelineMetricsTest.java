package ru.subsidy.docproc.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PipelineMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PipelineMetrics metrics = new PipelineMetrics(registry);

    @Test
    void stageTimerCountsOkAndError() {
        assertThat(metrics.time("text", () -> 42)).isEqualTo(42);
        assertThatThrownBy(() -> metrics.time("text", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get(PipelineMetrics.STAGE_TIMER).tags("stage", "text", "outcome", "ok").timer().count())
                .isEqualTo(1);
        assertThat(registry.get(PipelineMetrics.STAGE_TIMER).tags("stage", "text", "outcome", "error").timer()
                .count()).isEqualTo(1);
    }

    @Test
    void confidenceOnlyForModelDocuments() {
        metrics.documentParsed("INVOICE", new BigDecimal("0.930"), PipelineStore.MODEL, null);
        metrics.documentParsed("INVOICE", BigDecimal.ONE, "SPECIALIST", null);
        metrics.documentParsed("ESTIMATE", new BigDecimal("0.400"), PipelineStore.MODEL, "TYPE_UNKNOWN");
        assertThat(registry.get(PipelineMetrics.CONFIDENCE).tags("type", "INVOICE").summary().count()).isEqualTo(1);
        assertThat(registry.get(PipelineMetrics.CONFIDENCE).tags("type", "INVOICE").summary().mean())
                .isEqualTo(0.93);
        assertThat(registry.get(PipelineMetrics.DOCUMENTS).tags("type", "INVOICE", "reason", "NONE").counters())
                .hasSize(2);
        assertThat(registry.get(PipelineMetrics.DOCUMENTS).tags("reason", "TYPE_UNKNOWN").counter().count())
                .isEqualTo(1);
    }

    @Test
    void filesAndRetries() {
        metrics.fileFinished("PARSED");
        metrics.fileFinished("PARSED");
        metrics.retried("MODEL_UNAVAILABLE");
        assertThat(registry.get(PipelineMetrics.FILES).tags("stage", "PARSED").counter().count()).isEqualTo(2);
        assertThat(registry.get(PipelineMetrics.RETRIES).tags("code", "MODEL_UNAVAILABLE").counter().count())
                .isEqualTo(1);
    }
}
