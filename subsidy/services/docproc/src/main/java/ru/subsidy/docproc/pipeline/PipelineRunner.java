package ru.subsidy.docproc.pipeline;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ru.subsidy.docproc.classify.Classifier;
import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.docproc.classify.Prompts;
import ru.subsidy.docproc.classify.Segment;
import ru.subsidy.docproc.classify.TypeRegistry;
import ru.subsidy.docproc.config.DocprocProperties;
import ru.subsidy.docproc.extract.FieldExtractor;
import ru.subsidy.docproc.extract.FieldExtractor.Extraction;
import ru.subsidy.docproc.normalize.FieldValidator;
import ru.subsidy.docproc.normalize.FieldValue;
import ru.subsidy.docproc.pipeline.PipelineStore.ParsedDocument;
import ru.subsidy.docproc.pipeline.PipelineStore.Pending;
import ru.subsidy.docproc.queue.Job;
import ru.subsidy.docproc.queue.JobQueue;
import ru.subsidy.docproc.queue.Stage;
import ru.subsidy.docproc.text.PackageClient;
import ru.subsidy.docproc.text.PageText;
import ru.subsidy.docproc.text.TextExtractor;
import ru.subsidy.llm.api.ModelInvalidOutputException;
import ru.subsidy.llm.api.ModelUnavailableException;

/**
 * Машина стадий одного файла: текст → сегменты и вид → поля → проверки. Внешние вызовы — вне транзакций.
 */
@Component
public class PipelineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineRunner.class);
    private static final String UNREADABLE_EVENT = "DocumentUnreadable";
    private static final String TYPE_UNKNOWN = "TYPE_UNKNOWN";
    private static final String TYPE_OTHER = "TYPE_OTHER";
    private static final String FIELDS_TO_CHECK = "FIELDS_TO_CHECK";
    private static final String SPECIALIST = "SPECIALIST";
    private static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    private static final int CONF_SCALE = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(10);

    private final PipelineStore store;
    private final JobQueue queue;
    private final PackageClient files;
    private final TextExtractor texts;
    private final Classifier classifier;
    private final FieldExtractor extractor;
    private final FieldValidator validator;
    private final TypeRegistry registry;
    private final DocprocProperties props;
    private final PipelineMetrics metrics;

    /**
     * Конструктор.
     *
     * @param store запись стадий
     * @param queue очередь
     * @param files файлы package
     * @param texts текст страниц
     * @param classifier вид и сегменты
     * @param extractor поля
     * @param validator проверки
     * @param registry реестр видов
     * @param props настройки
     * @param metrics метрики стадий
     */
    public PipelineRunner(PipelineStore store, JobQueue queue, PackageClient files, TextExtractor texts,
                          Classifier classifier, FieldExtractor extractor, FieldValidator validator,
                          TypeRegistry registry, DocprocProperties props,
                          PipelineMetrics metrics) {
        this.store = store;
        this.queue = queue;
        this.files = files;
        this.texts = texts;
        this.classifier = classifier;
        this.extractor = extractor;
        this.validator = validator;
        this.registry = registry;
        this.props = props;
        this.metrics = metrics;
    }

    /**
     * Обрабатывает задание с текущей стадии; ошибки переводит в WAITING_MODEL, повтор или FAILED.
     *
     * @param job задание
     */
    public void run(Job job) {
        FileBytes content = new FileBytes(job);
        try {
            process(job, content);
        } catch (ModelUnavailableException unavailable) {
            LOG.info("model unavailable file={}", job.fileId());
            store.changeStage(job, Stage.WAITING_MODEL, MODEL_UNAVAILABLE);
            metrics.retried(MODEL_UNAVAILABLE);
            queue.postpone(job, props.modelRetryDelay(), false, MODEL_UNAVAILABLE);
        } catch (ModelInvalidOutputException invalid) {
            retryOrFail(job, "MODEL_INVALID_OUTPUT");
        } catch (RuntimeException failure) {
            LOG.warn("pipeline failed file={} stage={}: {}", job.fileId(), job.stage(),
                    failure.getClass().getSimpleName(), failure);
            retryOrFail(job, "PIPELINE_ERROR");
        }
    }

    private void process(Job job, FileBytes content) {
        pagesOf(job, content).ifPresent(pages -> {
            if (pages.stream().noneMatch(PageText::hasText) && !props.visionFallback()) {
                finish(job, Stage.UNREADABLE, "TEXT_NOT_FOUND", UNREADABLE_EVENT);
            } else {
                segmentsOf(job, content, pages).ifPresent(docs -> parseAll(job, content, pages, docs));
            }
        });
    }

    private Optional<List<PageText>> pagesOf(Job job, FileBytes content) {
        Optional<List<PageText>> saved = store.loadText(job);
        if (saved.isPresent()) {
            return saved;
        }
        if (!store.changeStage(job, Stage.EXTRACTING_TEXT, null)) {
            return Optional.empty();
        }
        List<PageText> pages = metrics.time("text", () -> texts.extract(content.get(), job.mime()));
        return store.saveText(job, pages) ? Optional.of(pages) : Optional.empty();
    }

    private Optional<List<DocSegment>> segmentsOf(Job job, FileBytes content, List<PageText> pages) {
        Optional<List<DocSegment>> docs = store.loadSegments(job);
        if (docs.isEmpty()) {
            List<Segment> segments = metrics.time("segment", () -> classifier.segment(job.fileId().toString(),
                    pages, images(job, content, pages, 1, pages.size())));
            docs = store.saveSegments(job, segments);
        }
        return docs;
    }

    private void parseAll(Job job, FileBytes content, List<PageText> pages, List<DocSegment> docs) {
        for (DocSegment doc : docs) {
            Optional<Pending> pending = store.pending(doc.documentId());
            if (pending.isPresent() || !store.isParsed(doc.documentId())) {
                parse(job, content, pages, doc, pending.orElse(null));
            }
        }
        finish(job, store.needsReview(job) ? Stage.NEEDS_REVIEW : Stage.PARSED, null, null);
    }

    private void parse(Job job, FileBytes content, List<PageText> pages, DocSegment doc, Pending pending) {
        Segment seg = doc.segment();
        final boolean manual = pending != null;
        DocType type = registry.orOther(manual ? pending.type() : seg.type());
        BigDecimal confidence = manual ? BigDecimal.ONE.setScale(CONF_SCALE) : seg.confidence();
        String text = docText(pages, seg);
        Extraction extraction = metrics.time("fields", () -> extractor.extract(doc.documentId().toString(), type,
                text, images(job, content, pages, seg.fromPage(), seg.toPage())));
        List<FieldValue> fields = validator.normalize(type, extraction.data(), extraction.confidence(),
                props.fieldReviewThreshold());
        String reason = reasonOf(confidence, type, fields);
        store.saveResult(new ParsedDocument(job.fileId(), job.packageId(), job.version(), doc.documentId(),
                seg.fromPage(), seg.toPage(), type.code(), confidence, manual ? null : seg.evidence(),
                manual ? SPECIALIST : PipelineStore.MODEL, fields, extraction.model(),
                Prompts.SEGMENT + "+" + Prompts.EXTRACT, type.version(), reason != null, reason,
                manual ? pending.reason() : null, manual ? pending.by() : null));
        metrics.documentParsed(type.code(), confidence, manual ? SPECIALIST : PipelineStore.MODEL, reason);
    }

    private String docText(List<PageText> pages, Segment seg) {
        String text = pages.stream().filter(page -> page.page() >= seg.fromPage() && page.page() <= seg.toPage())
                .filter(PageText::hasText).map(PageText::textWithTables).collect(Collectors.joining("\n\n"));
        return text.length() > props.maxCharsPerDocument() ? text.substring(0, props.maxCharsPerDocument()) : text;
    }

    private String reasonOf(BigDecimal confidence, DocType type, List<FieldValue> fields) {
        String reason = null;
        if (confidence.compareTo(props.classifyMinConfidence()) < 0) {
            reason = TYPE_UNKNOWN;
        } else if (TypeRegistry.OTHER.equals(type.code())) {
            reason = TYPE_OTHER;
        } else if (fields.stream().anyMatch(FieldValue::failed)) {
            reason = FIELDS_TO_CHECK;
        }
        return reason;
    }

    private List<byte[]> images(Job job, FileBytes content, List<PageText> pages, int fromPage, int toPage) {
        if (!props.visionFallback()) {
            return List.of();
        }
        boolean missing = pages.stream().filter(page -> page.page() >= fromPage && page.page() <= toPage)
                .anyMatch(PageText::needsImage);
        return missing ? texts.images(content.get(), job.mime(), fromPage, toPage) : new ArrayList<>();
    }

    private void retryOrFail(Job job, String code) {
        if (job.attempts() + 1 >= props.maxAttempts()) {
            finish(job, Stage.FAILED, code, "DocumentFailed");
        } else {
            metrics.retried(code);
            queue.postpone(job, BASE_BACKOFF.multipliedBy(1L << job.attempts()), true, code);
        }
    }

    private void finish(Job job, String stage, String reason, String eventType) {
        store.finish(job, stage, reason, eventType);
        metrics.fileFinished(stage);
    }

    /**
     * Ленивое скачивание файла: только если стадии он действительно нужен.
     */
    private final class FileBytes {

        private final Job job;
        private byte[] bytes;

        private FileBytes(Job job) {
            this.job = job;
        }

        private byte[] get() {
            if (bytes == null) {
                bytes = files.download(job.fileId(), job.version());
            }
            return bytes;
        }
    }
}
