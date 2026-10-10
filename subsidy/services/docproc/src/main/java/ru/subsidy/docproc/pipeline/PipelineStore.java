package ru.subsidy.docproc.pipeline;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.outbox.OutboxWriter;
import ru.subsidy.docproc.classify.Segment;
import ru.subsidy.docproc.jooq.Tables;
import ru.subsidy.docproc.normalize.FieldValue;
import ru.subsidy.docproc.queue.Job;
import ru.subsidy.docproc.queue.JobQueue;
import ru.subsidy.docproc.queue.Stage;
import ru.subsidy.docproc.text.PageText;

/**
 * Запись результатов стадий: каждая стадия — одна короткая транзакция с outbox (рестарт продолжает с места).
 */
@Component
public class PipelineStore {

    /** Источник результата — модель. */
    public static final String MODEL = "MODEL";
    private static final String TEXT = "TEXT";
    private static final String SEGMENTS = "SEGMENTS";
    private static final String FILE_ID = "fileId";
    private static final String PACKAGE_ID = "packageId";
    private static final String VERSION = "version";
    private static final String STAGE = "stage";
    private static final String REASON = "reasonCode";
    private static final String DOCUMENT_ID = "documentId";
    private static final String FROM_PAGE = "fromPage";
    private static final String TO_PAGE = "toPage";

    private final DSLContext dsl;
    private final JobQueue queue;
    private final OutboxWriter outbox;
    private final ObjectMapper json;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param queue очередь
     * @param outbox outbox
     * @param json Jackson
     * @param clock часы
     */
    public PipelineStore(DSLContext dsl, JobQueue queue, OutboxWriter outbox, ObjectMapper json, Clock clock) {
        this.dsl = dsl;
        this.queue = queue;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Меняет стадию файла и сообщает о ней.
     *
     * @param job задание
     * @param stage стадия
     * @param reason причина или null
     * @return false — задание устарело (файл заменён или удалён)
     */
    @Transactional
    public boolean changeStage(Job job, String stage, String reason) {
        if (!queue.moveTo(job, stage, reason)) {
            return false;
        }
        stageChanged(job, stage, reason);
        return true;
    }

    /**
     * Текст страниц, если стадия уже пройдена.
     *
     * @param job задание
     * @return страницы
     */
    @Transactional(readOnly = true)
    public Optional<List<PageText>> loadText(Job job) {
        return output(job, TEXT).map(raw -> read(raw, new TypeReference<List<PageText>>() { }));
    }

    /**
     * Сохраняет текст и переходит к классификации.
     *
     * @param job задание
     * @param pages страницы
     * @return false — задание устарело
     */
    @Transactional
    public boolean saveText(Job job, List<PageText> pages) {
        if (!queue.moveTo(job, Stage.CLASSIFYING, null)) {
            return false;
        }
        saveOutput(job, TEXT, pages);
        stageChanged(job, Stage.CLASSIFYING, null);
        return true;
    }

    /**
     * Документы файла, если сегментация уже пройдена.
     *
     * @param job задание
     * @return документы
     */
    @Transactional(readOnly = true)
    public Optional<List<DocSegment>> loadSegments(Job job) {
        return output(job, SEGMENTS).map(raw -> read(raw, new TypeReference<List<DocSegment>>() { }));
    }

    /**
     * Сохраняет документы файла (старые версии помечаются удалёнными).
     *
     * @param job задание
     * @param segments сегменты
     * @return документы или пусто, если задание устарело
     */
    @Transactional
    public Optional<List<DocSegment>> saveSegments(Job job, List<Segment> segments) {
        if (!queue.moveTo(job, Stage.EXTRACTING_FIELDS, null)) {
            return Optional.empty();
        }
        final OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.update(Tables.DOCUMENTS).set(Tables.DOCUMENTS.REMOVED, true).set(Tables.DOCUMENTS.UPDATED_AT, now)
                .where(Tables.DOCUMENTS.FILE_ID.eq(job.fileId())).execute();
        List<DocSegment> docs = new ArrayList<>();
        List<Map<String, Object>> payloadDocs = new ArrayList<>();
        for (Segment seg : segments) {
            UUID documentId = UuidCreator.getTimeOrderedEpoch();
            dsl.insertInto(Tables.DOCUMENTS)
                    .set(Tables.DOCUMENTS.ID, documentId)
                    .set(Tables.DOCUMENTS.FILE_ID, job.fileId())
                    .set(Tables.DOCUMENTS.PACKAGE_ID, job.packageId())
                    .set(Tables.DOCUMENTS.FILE_VERSION, job.version())
                    .set(Tables.DOCUMENTS.FROM_PAGE, seg.fromPage())
                    .set(Tables.DOCUMENTS.TO_PAGE, seg.toPage())
                    .set(Tables.DOCUMENTS.STAGE, Stage.EXTRACTING_FIELDS)
                    .set(Tables.DOCUMENTS.CREATED_AT, now)
                    .set(Tables.DOCUMENTS.UPDATED_AT, now)
                    .execute();
            docs.add(new DocSegment(documentId, seg));
            payloadDocs.add(Map.of(DOCUMENT_ID, documentId.toString(), FROM_PAGE, seg.fromPage(),
                    TO_PAGE, seg.toPage()));
        }
        saveOutput(job, SEGMENTS, docs);
        Map<String, Object> payload = base(job);
        payload.put("documents", payloadDocs);
        outbox.append(Topics.DOCPROC_DOCUMENT_V1, "DocumentsSegmented", job.fileId().toString(), payload);
        stageChanged(job, Stage.EXTRACTING_FIELDS, null);
        return Optional.of(docs);
    }

    /**
     * Разобран ли документ.
     *
     * @param documentId документ
     * @return true, если есть результат
     */
    @Transactional(readOnly = true)
    public boolean isParsed(UUID documentId) {
        return dsl.fetchExists(Tables.PARSE_RESULTS, Tables.PARSE_RESULTS.DOCUMENT_ID.eq(documentId));
    }

    /**
     * Сохраняет результат документа и публикует DocumentParsed.
     *
     * @param result результат
     */
    @Transactional
    public void saveResult(ParsedDocument result) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        Integer last = dsl.select(DSL.max(Tables.PARSE_RESULTS.VERSION)).from(Tables.PARSE_RESULTS)
                .where(Tables.PARSE_RESULTS.DOCUMENT_ID.eq(result.documentId())).fetchOne(0, Integer.class);
        final int version = last == null ? 1 : last + 1;
        final String stage = result.needsReview() ? Stage.NEEDS_REVIEW : Stage.PARSED;
        dsl.insertInto(Tables.PARSE_RESULTS)
                .set(Tables.PARSE_RESULTS.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.PARSE_RESULTS.DOCUMENT_ID, result.documentId())
                .set(Tables.PARSE_RESULTS.VERSION, version)
                .set(Tables.PARSE_RESULTS.DOC_TYPE, result.type())
                .set(Tables.PARSE_RESULTS.TYPE_CONFIDENCE, result.typeConfidence())
                .set(Tables.PARSE_RESULTS.EVIDENCE, result.evidence())
                .set(Tables.PARSE_RESULTS.SOURCE, result.source())
                .set(Tables.PARSE_RESULTS.FIELDS, JSONB.valueOf(write(result.fields())))
                .set(Tables.PARSE_RESULTS.MODEL, result.model())
                .set(Tables.PARSE_RESULTS.PROMPT_VERSION, result.promptVersion())
                .set(Tables.PARSE_RESULTS.SCHEMA_VERSION, result.schemaVersion())
                .set(Tables.PARSE_RESULTS.REASON, result.reason())
                .set(Tables.PARSE_RESULTS.CREATED_BY, result.createdBy())
                .set(Tables.PARSE_RESULTS.CREATED_AT, now)
                .execute();
        dsl.update(Tables.DOCUMENTS).set(Tables.DOCUMENTS.STAGE, stage)
                .set(Tables.DOCUMENTS.REASON_CODE, result.reasonCode())
                .set(Tables.DOCUMENTS.PENDING_TYPE, (String) null)
                .set(Tables.DOCUMENTS.PENDING_REASON, (String) null)
                .set(Tables.DOCUMENTS.PENDING_BY, (String) null)
                .set(Tables.DOCUMENTS.UPDATED_AT, now)
                .where(Tables.DOCUMENTS.ID.eq(result.documentId())).execute();
        Map<String, Object> payload = new HashMap<>();
        payload.put(FILE_ID, result.fileId().toString());
        payload.put(PACKAGE_ID, result.packageId().toString());
        payload.put(VERSION, result.fileVersion());
        payload.put(DOCUMENT_ID, result.documentId().toString());
        payload.put(FROM_PAGE, result.fromPage());
        payload.put(TO_PAGE, result.toPage());
        payload.put("type", result.type());
        payload.put("typeConfidence", result.typeConfidence());
        payload.put("source", result.source());
        payload.put(STAGE, stage);
        payload.put(REASON, result.reasonCode());
        payload.put("resultVersion", version);
        payload.put("fields", result.fields().stream().map(PipelineStore::fieldPayload).toList());
        outbox.append(Topics.DOCPROC_DOCUMENT_V1, "DocumentParsed", result.fileId().toString(), payload);
    }

    /**
     * Запрошенная специалистом смена вида документа.
     *
     * @param documentId документ
     * @return вид, причина и автор
     */
    @Transactional(readOnly = true)
    public Optional<Pending> pending(UUID documentId) {
        return dsl.select(Tables.DOCUMENTS.PENDING_TYPE, Tables.DOCUMENTS.PENDING_REASON, Tables.DOCUMENTS.PENDING_BY)
                .from(Tables.DOCUMENTS).where(Tables.DOCUMENTS.ID.eq(documentId))
                .and(Tables.DOCUMENTS.PENDING_TYPE.isNotNull())
                .fetchOptional(rec -> new Pending(rec.value1(), rec.value2(), rec.value3()));
    }

    /**
     * Есть ли в файле документы на ручную проверку.
     *
     * @param job задание
     * @return true, если хоть один NEEDS_REVIEW
     */
    @Transactional(readOnly = true)
    public boolean needsReview(Job job) {
        return dsl.fetchExists(Tables.DOCUMENTS, Tables.DOCUMENTS.FILE_ID.eq(job.fileId())
                .and(Tables.DOCUMENTS.REMOVED.isFalse()).and(Tables.DOCUMENTS.STAGE.eq(Stage.NEEDS_REVIEW)));
    }

    /**
     * Завершает задание финальной стадией.
     *
     * @param job задание
     * @param stage PARSED, NEEDS_REVIEW, UNREADABLE или FAILED
     * @param reason причина
     * @param eventType DocumentUnreadable / DocumentFailed или null
     */
    @Transactional
    public void finish(Job job, String stage, String reason, String eventType) {
        if (!queue.moveTo(job, stage, reason)) {
            return;
        }
        if (eventType != null) {
            Map<String, Object> payload = base(job);
            payload.put(REASON, reason);
            outbox.append(Topics.DOCPROC_DOCUMENT_V1, eventType, job.fileId().toString(), payload);
        }
        stageChanged(job, stage, reason);
    }

    private static Map<String, Object> fieldPayload(FieldValue field) {
        Map<String, Object> out = new HashMap<>();
        out.put("name", field.name());
        out.put("value", field.value());
        out.put("confidence", field.confidence());
        out.put("checks", field.checks());
        return out;
    }

    private void stageChanged(Job job, String stage, String reason) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.STAGE_EVENTS)
                .set(Tables.STAGE_EVENTS.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.STAGE_EVENTS.FILE_ID, job.fileId())
                .set(Tables.STAGE_EVENTS.STAGE, stage)
                .set(Tables.STAGE_EVENTS.REASON, reason)
                .set(Tables.STAGE_EVENTS.AT, now)
                .execute();
        Map<String, Object> payload = base(job);
        payload.put(STAGE, stage);
        payload.put(REASON, reason);
        outbox.append(Topics.DOCPROC_DOCUMENT_V1, "StageChanged", job.fileId().toString(), payload);
    }

    private static Map<String, Object> base(Job job) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(FILE_ID, job.fileId().toString());
        payload.put(PACKAGE_ID, job.packageId().toString());
        payload.put(VERSION, job.version());
        return payload;
    }

    private Optional<String> output(Job job, String stage) {
        return dsl.select(Tables.STAGE_OUTPUTS.OUTPUT).from(Tables.STAGE_OUTPUTS)
                .where(Tables.STAGE_OUTPUTS.FILE_ID.eq(job.fileId()))
                .and(Tables.STAGE_OUTPUTS.VERSION.eq(job.version()))
                .and(Tables.STAGE_OUTPUTS.STAGE.eq(stage))
                .fetchOptional(Tables.STAGE_OUTPUTS.OUTPUT).map(JSONB::data);
    }

    private void saveOutput(Job job, String stage, Object value) {
        JSONB data = JSONB.valueOf(write(value));
        dsl.insertInto(Tables.STAGE_OUTPUTS)
                .set(Tables.STAGE_OUTPUTS.FILE_ID, job.fileId())
                .set(Tables.STAGE_OUTPUTS.VERSION, job.version())
                .set(Tables.STAGE_OUTPUTS.STAGE, stage)
                .set(Tables.STAGE_OUTPUTS.OUTPUT, data)
                .set(Tables.STAGE_OUTPUTS.CREATED_AT, OffsetDateTime.now(clock))
                .onConflict(Tables.STAGE_OUTPUTS.FILE_ID, Tables.STAGE_OUTPUTS.VERSION, Tables.STAGE_OUTPUTS.STAGE)
                .doUpdate().set(Tables.STAGE_OUTPUTS.OUTPUT, data)
                .execute();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Результат стадии не сериализуется", failure);
        }
    }

    private <T> T read(String raw, TypeReference<T> type) {
        try {
            return json.readValue(raw, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Результат стадии не читается", failure);
        }
    }

    /**
     * Результат разбора документа.
     *
     * @param fileId файл
     * @param packageId пакет
     * @param fileVersion версия файла
     * @param documentId документ
     * @param fromPage первая страница
     * @param toPage последняя страница
     * @param type вид
     * @param typeConfidence уверенность вида
     * @param evidence признак вида
     * @param source MODEL или SPECIALIST
     * @param fields поля
     * @param model модель
     * @param promptVersion версия промпта
     * @param schemaVersion версия схемы
     * @param needsReview нужна ручная проверка
     * @param reasonCode причина ручной проверки
     * @param reason причина смены вида специалистом
     * @param createdBy кто сменил вид
     */
    public record ParsedDocument(UUID fileId, UUID packageId, int fileVersion, UUID documentId, int fromPage,
                                 int toPage, String type, BigDecimal typeConfidence, String evidence, String source,
                                 List<FieldValue> fields, String model, String promptVersion, String schemaVersion,
                                 boolean needsReview, String reasonCode, String reason, String createdBy) {
    }

    /**
     * Смена вида, ожидающая переизвлечения.
     *
     * @param type новый вид
     * @param reason причина
     * @param by кто
     */
    public record Pending(String type, String reason, String by) {
    }
}
