package ru.subsidy.package_.pipeline;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.package_.jooq.Tables;

/**
 * События конвейера (docproc, rules) → стадии файлов и документов, статус пакета (FR-62).
 * Идемпотентно: eventId пишется в processed_events в той же транзакции.
 */
@Service
public class PipelineEventsService {

    /** SSE: стадия файла. */
    public static final String EVENT_FILE_STAGE = "file.stage";
    /** SSE: документ разобран. */
    public static final String EVENT_DOCUMENT = "document.parsed";
    /** SSE: протокол готов. */
    public static final String EVENT_PACKAGE_CHECKED = "package.checked";
    private static final String PROCESSING = "PROCESSING";
    private static final String CHECKED = "CHECKED";
    private static final String FILE_ID = "fileId";
    private static final String DOCUMENT_ID = "documentId";
    private static final String PACKAGE_ID = "packageId";
    private static final String STAGE = "stage";
    private static final String REASON = "reasonCode";
    private static final String UNREADABLE_EVENT = "DocumentUnreadable";
    private static final String VERSION = "version";
    private static final String DOCUMENTS = "documents";
    private static final String FROM_PAGE = "fromPage";
    private static final String TO_PAGE = "toPage";
    private static final String SEGMENTED = "SEGMENTED";
    private static final String TYPE = "type";
    private static final String TYPE_CONFIDENCE = "typeConfidence";
    private static final String MODEL = "MODEL";
    private static final String SOURCE = "source";
    private static final String PARSED = "PARSED";
    private static final String PROTOCOL_STATUS = "protocolStatus";
    private static final String COMPLETENESS = "completeness";
    private static final String PROTOCOL_ID = "protocolId";

    private final DSLContext dsl;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     */
    public PipelineEventsService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    /**
     * Обрабатывает событие конвейера.
     *
     * @param eventId id события
     * @param type тип события
     * @param payload payload
     * @return что показать по SSE; пусто — повтор, чужое или неизвестное событие
     */
    @Transactional
    public Optional<PackageUpdate> handle(UUID eventId, String type, JsonNode payload) {
        int fresh = dsl.insertInto(Tables.PROCESSED_EVENTS)
                .set(Tables.PROCESSED_EVENTS.EVENT_ID, eventId)
                .set(Tables.PROCESSED_EVENTS.PROCESSED_AT, OffsetDateTime.now(clock))
                .onConflictDoNothing().execute();
        if (fresh == 0) {
            return Optional.empty();
        }
        return switch (type) {
            case "StageChanged" -> stageChanged(payload);
            case "DocumentsSegmented" -> segmented(payload);
            case "DocumentParsed" -> parsed(payload);
            case UNREADABLE_EVENT, "DocumentFailed" -> documentProblem(type, payload);
            case "PackageChecked" -> checked(payload);
            case null, default -> Optional.empty();
        };
    }

    private Optional<PackageUpdate> stageChanged(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        String stage = payload.path(STAGE).asText();
        String reason = text(payload, REASON);
        Optional<UUID> packageId = dsl.update(Tables.FILES)
                .set(Tables.FILES.STAGE, stage)
                .set(Tables.FILES.REASON_CODE, reason)
                .set(Tables.FILES.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.FILES.ID.eq(fileId))
                .returning(Tables.FILES.PACKAGE_ID)
                .fetchOptional(Tables.FILES.PACKAGE_ID);
        packageId.ifPresent(this::markProcessing);
        return packageId.map(id -> {
            Map<String, Object> data = new HashMap<>();
            data.put(FILE_ID, fileId.toString());
            data.put(STAGE, stage);
            data.put(REASON, reason);
            return new PackageUpdate(id, EVENT_FILE_STAGE, data);
        });
    }

    private Optional<PackageUpdate> segmented(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        Optional<UUID> packageId = packageOfFile(fileId);
        if (packageId.isEmpty()) {
            return Optional.empty();
        }
        int version = payload.path(VERSION).asInt(currentVersion(fileId));
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (JsonNode doc : payload.path(DOCUMENTS)) {
            dsl.insertInto(Tables.FILE_DOCUMENTS)
                    .set(Tables.FILE_DOCUMENTS.DOCUMENT_ID, uuid(doc, DOCUMENT_ID))
                    .set(Tables.FILE_DOCUMENTS.FILE_ID, fileId)
                    .set(Tables.FILE_DOCUMENTS.FILE_VERSION, version)
                    .set(Tables.FILE_DOCUMENTS.FROM_PAGE, doc.path(FROM_PAGE).asInt(1))
                    .set(Tables.FILE_DOCUMENTS.TO_PAGE, doc.path(TO_PAGE).asInt(1))
                    .set(Tables.FILE_DOCUMENTS.STAGE, SEGMENTED)
                    .set(Tables.FILE_DOCUMENTS.UPDATED_AT, now)
                    .onConflictDoNothing().execute();
        }
        return packageId.map(id -> new PackageUpdate(id, EVENT_FILE_STAGE,
                Map.of(FILE_ID, fileId.toString(), STAGE, SEGMENTED, DOCUMENTS, payload.path(DOCUMENTS).size())));
    }

    private Optional<PackageUpdate> parsed(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        UUID documentId = uuid(payload, DOCUMENT_ID);
        Optional<UUID> packageId = packageOfFile(fileId);
        if (packageId.isEmpty()) {
            return Optional.empty();
        }
        String docType = text(payload, TYPE);
        BigDecimal confidence = payload.hasNonNull(TYPE_CONFIDENCE)
                ? payload.path(TYPE_CONFIDENCE).decimalValue() : null;
        String source = Optional.ofNullable(text(payload, SOURCE)).orElse(MODEL);
        String stage = Optional.ofNullable(text(payload, STAGE)).orElse(PARSED);
        String reason = text(payload, REASON);
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.FILE_DOCUMENTS)
                .set(Tables.FILE_DOCUMENTS.DOCUMENT_ID, documentId)
                .set(Tables.FILE_DOCUMENTS.FILE_ID, fileId)
                .set(Tables.FILE_DOCUMENTS.FILE_VERSION, payload.path(VERSION).asInt(currentVersion(fileId)))
                .set(Tables.FILE_DOCUMENTS.FROM_PAGE, payload.path(FROM_PAGE).asInt(1))
                .set(Tables.FILE_DOCUMENTS.TO_PAGE, payload.path(TO_PAGE).asInt(1))
                .set(Tables.FILE_DOCUMENTS.DOC_TYPE, docType)
                .set(Tables.FILE_DOCUMENTS.TYPE_CONFIDENCE, confidence)
                .set(Tables.FILE_DOCUMENTS.SOURCE, source)
                .set(Tables.FILE_DOCUMENTS.STAGE, stage)
                .set(Tables.FILE_DOCUMENTS.REASON_CODE, reason)
                .set(Tables.FILE_DOCUMENTS.UPDATED_AT, now)
                .onConflict(Tables.FILE_DOCUMENTS.DOCUMENT_ID).doUpdate()
                .set(Tables.FILE_DOCUMENTS.DOC_TYPE, docType)
                .set(Tables.FILE_DOCUMENTS.TYPE_CONFIDENCE, confidence)
                .set(Tables.FILE_DOCUMENTS.SOURCE, source)
                .set(Tables.FILE_DOCUMENTS.STAGE, stage)
                .set(Tables.FILE_DOCUMENTS.REASON_CODE, reason)
                .set(Tables.FILE_DOCUMENTS.UPDATED_AT, now)
                .execute();
        Map<String, Object> data = new HashMap<>();
        data.put(FILE_ID, fileId.toString());
        data.put(DOCUMENT_ID, documentId.toString());
        data.put(STAGE, stage);
        data.put(REASON, reason);
        data.put(TYPE, docType);
        data.put(TYPE_CONFIDENCE, confidence);
        return packageId.map(id -> new PackageUpdate(id, EVENT_DOCUMENT, data));
    }

    private Optional<PackageUpdate> documentProblem(String type, JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        String stage = UNREADABLE_EVENT.equals(type) ? "UNREADABLE" : "FAILED";
        String reason = text(payload, REASON);
        if (payload.hasNonNull(DOCUMENT_ID)) {
            dsl.update(Tables.FILE_DOCUMENTS)
                    .set(Tables.FILE_DOCUMENTS.STAGE, stage)
                    .set(Tables.FILE_DOCUMENTS.REASON_CODE, reason)
                    .set(Tables.FILE_DOCUMENTS.UPDATED_AT, OffsetDateTime.now(clock))
                    .where(Tables.FILE_DOCUMENTS.DOCUMENT_ID.eq(uuid(payload, DOCUMENT_ID)))
                    .execute();
        } else {
            dsl.update(Tables.FILES)
                    .set(Tables.FILES.STAGE, stage)
                    .set(Tables.FILES.REASON_CODE, reason)
                    .set(Tables.FILES.UPDATED_AT, OffsetDateTime.now(clock))
                    .where(Tables.FILES.ID.eq(fileId))
                    .execute();
        }
        Map<String, Object> data = new HashMap<>();
        data.put(FILE_ID, fileId.toString());
        data.put(DOCUMENT_ID, text(payload, DOCUMENT_ID));
        data.put(STAGE, stage);
        data.put(REASON, reason);
        return packageOfFile(fileId).map(id -> new PackageUpdate(id, EVENT_DOCUMENT, data));
    }

    private Optional<PackageUpdate> checked(JsonNode payload) {
        UUID packageId = uuid(payload, PACKAGE_ID);
        final int updated = dsl.update(Tables.PACKAGES)
                .set(Tables.PACKAGES.STATUS, CHECKED)
                .set(Tables.PACKAGES.PROTOCOL_STATUS, text(payload, PROTOCOL_STATUS))
                .set(Tables.PACKAGES.COMPLETENESS, intOrNull(payload, COMPLETENESS))
                .set(Tables.PACKAGES.FAILED, payload.path("failed").asInt(0))
                .set(Tables.PACKAGES.WARNINGS, payload.path("warnings").asInt(0))
                .set(Tables.PACKAGES.NEEDS_REVIEW, payload.path("needsReview").asInt(0))
                .set(Tables.PACKAGES.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGES.ID.eq(packageId))
                .and(Tables.PACKAGES.STATUS.ne("DECIDED"))
                .execute();
        Map<String, Object> data = new HashMap<>();
        data.put(PACKAGE_ID, packageId.toString());
        data.put(PROTOCOL_STATUS, text(payload, PROTOCOL_STATUS));
        data.put(PROTOCOL_ID, text(payload, PROTOCOL_ID));
        data.put(COMPLETENESS, intOrNull(payload, COMPLETENESS));
        return updated == 0 ? Optional.empty() : Optional.of(new PackageUpdate(packageId, EVENT_PACKAGE_CHECKED, data));
    }

    private void markProcessing(UUID packageId) {
        dsl.update(Tables.PACKAGES)
                .set(Tables.PACKAGES.STATUS, PROCESSING)
                .set(Tables.PACKAGES.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGES.ID.eq(packageId))
                .and(Tables.PACKAGES.STATUS.in(List.of("COLLECTING", CHECKED)))
                .execute();
    }

    private Optional<UUID> packageOfFile(UUID fileId) {
        return dsl.select(Tables.FILES.PACKAGE_ID).from(Tables.FILES).where(Tables.FILES.ID.eq(fileId))
                .fetchOptional(Tables.FILES.PACKAGE_ID);
    }

    private int currentVersion(UUID fileId) {
        return dsl.select(Tables.FILES.CURRENT_VERSION).from(Tables.FILES).where(Tables.FILES.ID.eq(fileId))
                .fetchOptional(Tables.FILES.CURRENT_VERSION).orElse(1);
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asInt() : null;
    }
}
