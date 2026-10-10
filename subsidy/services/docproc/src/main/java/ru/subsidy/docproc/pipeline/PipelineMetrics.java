package ru.subsidy.docproc.pipeline;

import java.math.BigDecimal;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Метрики конвейера (NFR-07, /actuator/prometheus): длительность стадий, уверенность вида, исходы документов и файлов.
 * Теги — только из закрытых словарей (стадии, коды видов и причин): без идентификаторов и данных организаций.
 */
@Component
public class PipelineMetrics {

    static final String STAGE_TIMER = "docproc.stage.duration";
    static final String CONFIDENCE = "docproc.type.confidence";
    static final String DOCUMENTS = "docproc.documents";
    static final String FILES = "docproc.files.finished";
    static final String RETRIES = "docproc.retries";
    private static final String NONE = "NONE";
    private static final String STAGE = "stage";
    private static final String TYPE = "type";
    private static final double[] CONFIDENCE_SLO = {0.5, 0.7, 0.8, 0.9, 0.95};

    private final MeterRegistry registry;

    /**
     * Конструктор.
     *
     * @param registry реестр метрик
     */
    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Замеряет стадию (при исключении — тоже, с outcome=error).
     *
     * @param stage стадия: text, segment, fields
     * @param work работа
     * @param <T> результат
     * @return результат работы
     */
    public <T> T time(String stage, Supplier<T> work) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "error";
        try {
            T result = work.get();
            outcome = "ok";
            return result;
        } finally {
            sample.stop(Timer.builder(STAGE_TIMER).description("Длительность стадии разбора")
                    .tags(STAGE, stage, "outcome", outcome).publishPercentileHistogram().register(registry));
        }
    }

    /**
     * Документ разобран.
     *
     * @param type код вида
     * @param confidence уверенность вида
     * @param source MODEL или SPECIALIST
     * @param reason причина проверки или null
     */
    public void documentParsed(String type, BigDecimal confidence, String source, String reason) {
        Counter.builder(DOCUMENTS).description("Разобранные документы").tags(TYPE, type, "source", source,
                "reason", reason == null ? NONE : reason).register(registry).increment();
        if (PipelineStore.MODEL.equals(source) && confidence != null) {
            DistributionSummary.builder(CONFIDENCE).description("Уверенность модели в виде документа")
                    .tags(TYPE, type).serviceLevelObjectives(CONFIDENCE_SLO).register(registry)
                    .record(confidence.doubleValue());
        }
    }

    /**
     * Файл дошёл до финальной стадии.
     *
     * @param stage PARSED, NEEDS_REVIEW, UNREADABLE, FAILED
     */
    public void fileFinished(String stage) {
        Counter.builder(FILES).description("Файлы по финальной стадии").tags(STAGE, stage).register(registry)
                .increment();
    }

    /**
     * Задание отложено на повтор.
     *
     * @param code причина: MODEL_UNAVAILABLE, MODEL_INVALID_OUTPUT, PIPELINE_ERROR
     */
    public void retried(String code) {
        Counter.builder(RETRIES).description("Отложенные повторы разбора").tags("code", code).register(registry)
                .increment();
    }
}
