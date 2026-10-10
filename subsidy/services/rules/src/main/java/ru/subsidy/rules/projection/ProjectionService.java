package ru.subsidy.rules.projection;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.common.inbox.IdempotentConsumer;
import ru.subsidy.rules.history.OrgHistoryService;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.recheck.RecheckRequests;

/**
 * Проекция пакета из событий package и docproc (package_view, package_files, package_documents).
 * События разных топиков могут прийти в любом порядке — запись идемпотентна, перепроверка через debounce.
 */
@Service
public class ProjectionService {

    private static final String K_FAILED = "FAILED";
    private static final String K_UNREADABLE = "UNREADABLE";
    private static final String K_PARSED = "PARSED";
    private static final String K_SOURCE = "source";
    private static final String K_TYPE = "type";
    private static final String K_FIELDS = "fields";
    private static final String K_TYPE_CONFIDENCE = "typeConfidence";
    private static final String K_RESULT_VERSION = "resultVersion";
    private static final String K_DECISION = "decision";
    private static final String K_CREATED_BY = "createdBy";
    private static final String K_PROGRAM_CODE = "programCode";
    private static final String K_DOCUMENT_UNREADABLE = "DocumentUnreadable";
    private static final Logger LOG = LoggerFactory.getLogger(ProjectionService.class);
    private static final String PACKAGE_ID = "packageId";
    private static final String FILE_ID = "fileId";
    private static final String DOCUMENT_ID = "documentId";
    private static final String VERSION = "version";
    private static final String STAGE = "stage";
    private static final String REASON = "reasonCode";
    private static final String DECIDED = "DECIDED";
    private static final String COLLECTING = "COLLECTING";
    private static final String RECEIVED = "RECEIVED";
    private static final String INN = "inn";

    private final DSLContext dsl;
    private final IdempotentConsumer inbox;
    private final RecheckRequests recheck;
    private final OrgHistoryService history;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param inbox идемпотентность
     * @param recheck перепроверки
     * @param history история организации
     * @param clock часы
     */
    public ProjectionService(DSLContext dsl, IdempotentConsumer inbox, RecheckRequests recheck,
                             OrgHistoryService history, Clock clock) {
        this.dsl = dsl;
        this.inbox = inbox;
        this.recheck = recheck;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Обрабатывает событие один раз.
     *
     * @param eventId id события
     * @param type тип
     * @param payload данные
     */
    @Transactional
    public void handle(UUID eventId, String type, JsonNode payload) {
        if (!inbox.firstTime(eventId)) {
            return;
        }
        switch (type) {
            case "PackageCreated" -> created(payload);
            case "PackageDecided" -> decided(payload);
            case "FileReceived", "FileReplaced" -> fileReceived(payload);
            case "FileRemoved" -> fileRemoved(payload);
            case "StageChanged" -> stageChanged(payload);
            case "DocumentsSegmented" -> segmented(payload);
            case "DocumentParsed" -> parsed(payload);
            case K_DOCUMENT_UNREADABLE, "DocumentFailed" -> fileProblem(type, payload);
            case null, default -> LOG.trace("skip event {}", type);
        }
    }

    private void created(JsonNode payload) {
        UUID packageId = uuid(payload, PACKAGE_ID);
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.PACKAGE_VIEW)
                .set(Tables.PACKAGE_VIEW.PACKAGE_ID, packageId)
                .set(Tables.PACKAGE_VIEW.ORG_ID, optionalUuid(payload, "orgId"))
                .set(Tables.PACKAGE_VIEW.INN, text(payload, INN))
                .set(Tables.PACKAGE_VIEW.PROGRAM_CODE, payload.path(K_PROGRAM_CODE).asText())
                .set(Tables.PACKAGE_VIEW.STATUS, COLLECTING)
                .set(Tables.PACKAGE_VIEW.CREATED_BY, text(payload, K_CREATED_BY))
                .set(Tables.PACKAGE_VIEW.CREATED_AT, now)
                .set(Tables.PACKAGE_VIEW.UPDATED_AT, now)
                .onConflictDoNothing().execute();
        history.append(text(payload, INN), text(payload, K_CREATED_BY), "PACKAGE_CREATED", packageId,
                Map.of(K_PROGRAM_CODE, payload.path(K_PROGRAM_CODE).asText()));
        recheck.request(packageId);
    }

    private void decided(JsonNode payload) {
        UUID packageId = uuid(payload, PACKAGE_ID);
        String decision = payload.path(K_DECISION).asText();
        dsl.update(Tables.PACKAGE_VIEW)
                .set(Tables.PACKAGE_VIEW.STATUS, "RETURN".equals(decision) ? COLLECTING : DECIDED)
                .set(Tables.PACKAGE_VIEW.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGE_VIEW.PACKAGE_ID.eq(packageId)).execute();
        history.append(text(payload, INN), text(payload, "decidedBy"), "PACKAGE_DECIDED", packageId,
                Map.of(K_DECISION, decision));
    }

    private void fileReceived(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        UUID packageId = uuid(payload, PACKAGE_ID);
        int version = payload.path(VERSION).asInt(1);
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.PACKAGE_FILES)
                .set(Tables.PACKAGE_FILES.FILE_ID, fileId)
                .set(Tables.PACKAGE_FILES.PACKAGE_ID, packageId)
                .set(Tables.PACKAGE_FILES.VERSION, version)
                .set(Tables.PACKAGE_FILES.STAGE, RECEIVED)
                .set(Tables.PACKAGE_FILES.UPDATED_AT, now)
                .onConflict(Tables.PACKAGE_FILES.FILE_ID).doUpdate()
                .set(Tables.PACKAGE_FILES.VERSION, version)
                .set(Tables.PACKAGE_FILES.STAGE, RECEIVED)
                .set(Tables.PACKAGE_FILES.REASON_CODE, (String) null)
                .set(Tables.PACKAGE_FILES.REMOVED, false)
                .set(Tables.PACKAGE_FILES.UPDATED_AT, now)
                .where(Tables.PACKAGE_FILES.VERSION.le(version))
                .execute();
        dsl.update(Tables.PACKAGE_DOCUMENTS).set(Tables.PACKAGE_DOCUMENTS.REMOVED, true)
                .where(Tables.PACKAGE_DOCUMENTS.FILE_ID.eq(fileId)).and(Tables.PACKAGE_DOCUMENTS.FILE_VERSION.lt(version))
                .execute();
    }

    private void fileRemoved(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        dsl.update(Tables.PACKAGE_FILES).set(Tables.PACKAGE_FILES.REMOVED, true)
                .set(Tables.PACKAGE_FILES.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGE_FILES.FILE_ID.eq(fileId)).execute();
        dsl.update(Tables.PACKAGE_DOCUMENTS).set(Tables.PACKAGE_DOCUMENTS.REMOVED, true)
                .where(Tables.PACKAGE_DOCUMENTS.FILE_ID.eq(fileId)).execute();
        recheck.request(uuid(payload, PACKAGE_ID));
    }

    private void stageChanged(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        String stage = payload.path(STAGE).asText();
        int updated = dsl.update(Tables.PACKAGE_FILES)
                .set(Tables.PACKAGE_FILES.STAGE, stage)
                .set(Tables.PACKAGE_FILES.REASON_CODE, text(payload, REASON))
                .set(Tables.PACKAGE_FILES.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGE_FILES.FILE_ID.eq(fileId))
                .and(Tables.PACKAGE_FILES.VERSION.le(payload.path(VERSION).asInt(Integer.MAX_VALUE)))
                .execute();
        if (updated > 0 && terminal(stage)) {
            recheck.request(uuid(payload, PACKAGE_ID));
        }
    }

    private void segmented(JsonNode payload) {
        UUID fileId = uuid(payload, FILE_ID);
        List<UUID> current = new ArrayList<>();
        payload.path("documents").forEach(doc -> current.add(uuid(doc, DOCUMENT_ID)));
        dsl.update(Tables.PACKAGE_DOCUMENTS).set(Tables.PACKAGE_DOCUMENTS.REMOVED, true)
                .where(Tables.PACKAGE_DOCUMENTS.FILE_ID.eq(fileId))
                .and(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.notIn(current))
                .execute();
    }

    /**
     * Сверяет документы пакета с docproc (RUL-18): документы с результатом — upsert, если версия результата
     * в проекции старше или нет; документы, которых в docproc нет, — снимаются. Затем перепроверка.
     *
     * @param packageId пакет
     * @param documents документы docproc (DocumentResult)
     * @return итог сверки
     */
    @Transactional
    public Reconciled reconcile(UUID packageId, List<JsonNode> documents) {
        List<UUID> present = new ArrayList<>();
        int updated = 0;
        for (JsonNode doc : documents) {
            UUID documentId = uuid(doc, DOCUMENT_ID);
            present.add(documentId);
            if (doc.hasNonNull(K_RESULT_VERSION) && stale(documentId, doc.path(K_RESULT_VERSION).asInt())) {
                ObjectNode payload = doc.deepCopy();
                payload.put(PACKAGE_ID, packageId.toString());
                payload.set(VERSION, doc.path("fileVersion"));
                upsertDocument(payload);
                updated++;
            }
        }
        int removed = dsl.update(Tables.PACKAGE_DOCUMENTS).set(Tables.PACKAGE_DOCUMENTS.REMOVED, true)
                .set(Tables.PACKAGE_DOCUMENTS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGE_DOCUMENTS.PACKAGE_ID.eq(packageId))
                .and(Tables.PACKAGE_DOCUMENTS.REMOVED.isFalse())
                .and(present.isEmpty() ? DSL.trueCondition() : Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.notIn(present))
                .execute();
        recheck.request(packageId);
        return new Reconciled(documents.size(), updated, removed);
    }

    private boolean stale(UUID documentId, int resultVersion) {
        Record2<Integer, Boolean> stored = dsl.select(Tables.PACKAGE_DOCUMENTS.RESULT_VERSION,
                        Tables.PACKAGE_DOCUMENTS.REMOVED)
                .from(Tables.PACKAGE_DOCUMENTS).where(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.eq(documentId)).fetchOne();
        return stored == null || stored.value1() == null || stored.value1() < resultVersion
                || Boolean.TRUE.equals(stored.value2());
    }

    private void parsed(JsonNode payload) {
        upsertDocument(payload);
        recheck.request(uuid(payload, PACKAGE_ID));
    }

    private void upsertDocument(JsonNode payload) {
        UUID documentId = uuid(payload, DOCUMENT_ID);
        UUID packageId = uuid(payload, PACKAGE_ID);
        Integer resultVersion = payload.hasNonNull(K_RESULT_VERSION) ? payload.path(K_RESULT_VERSION).asInt() : null;
        BigDecimal confidence = payload.hasNonNull(K_TYPE_CONFIDENCE) ? payload.path(K_TYPE_CONFIDENCE).decimalValue()
                : null;
        JSONB fields = JSONB.valueOf(payload.path(K_FIELDS).isArray() ? payload.path(K_FIELDS).toString() : "[]");
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.PACKAGE_DOCUMENTS)
                .set(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID, documentId)
                .set(Tables.PACKAGE_DOCUMENTS.PACKAGE_ID, packageId)
                .set(Tables.PACKAGE_DOCUMENTS.FILE_ID, uuid(payload, FILE_ID))
                .set(Tables.PACKAGE_DOCUMENTS.FILE_VERSION, payload.path(VERSION).asInt(1))
                .set(Tables.PACKAGE_DOCUMENTS.FROM_PAGE, payload.path("fromPage").asInt(1))
                .set(Tables.PACKAGE_DOCUMENTS.TO_PAGE, payload.path("toPage").asInt(1))
                .set(Tables.PACKAGE_DOCUMENTS.TYPE, text(payload, K_TYPE))
                .set(Tables.PACKAGE_DOCUMENTS.TYPE_CONFIDENCE, confidence)
                .set(Tables.PACKAGE_DOCUMENTS.SOURCE, text(payload, K_SOURCE))
                .set(Tables.PACKAGE_DOCUMENTS.STAGE, payload.path(STAGE).asText(K_PARSED))
                .set(Tables.PACKAGE_DOCUMENTS.REASON_CODE, text(payload, REASON))
                .set(Tables.PACKAGE_DOCUMENTS.RESULT_VERSION, resultVersion)
                .set(Tables.PACKAGE_DOCUMENTS.FIELDS, fields)
                .set(Tables.PACKAGE_DOCUMENTS.UPDATED_AT, now)
                .onConflict(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID).doUpdate()
                .set(Tables.PACKAGE_DOCUMENTS.TYPE, text(payload, K_TYPE))
                .set(Tables.PACKAGE_DOCUMENTS.TYPE_CONFIDENCE, confidence)
                .set(Tables.PACKAGE_DOCUMENTS.SOURCE, text(payload, K_SOURCE))
                .set(Tables.PACKAGE_DOCUMENTS.STAGE, payload.path(STAGE).asText(K_PARSED))
                .set(Tables.PACKAGE_DOCUMENTS.REASON_CODE, text(payload, REASON))
                .set(Tables.PACKAGE_DOCUMENTS.RESULT_VERSION, resultVersion)
                .set(Tables.PACKAGE_DOCUMENTS.FIELDS, fields)
                .set(Tables.PACKAGE_DOCUMENTS.REMOVED, false)
                .set(Tables.PACKAGE_DOCUMENTS.UPDATED_AT, now)
                .execute();
    }

    private void fileProblem(String type, JsonNode payload) {
        String stage = K_DOCUMENT_UNREADABLE.equals(type) ? K_UNREADABLE : K_FAILED;
        dsl.update(Tables.PACKAGE_FILES)
                .set(Tables.PACKAGE_FILES.STAGE, stage)
                .set(Tables.PACKAGE_FILES.REASON_CODE, text(payload, REASON))
                .set(Tables.PACKAGE_FILES.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.PACKAGE_FILES.FILE_ID.eq(uuid(payload, FILE_ID))).execute();
        recheck.request(uuid(payload, PACKAGE_ID));
    }

    private static boolean terminal(String stage) {
        return List.of(K_PARSED, "NEEDS_REVIEW", K_UNREADABLE, K_FAILED).contains(stage);
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        return Optional.ofNullable(text(node, field)).map(UUID::fromString).orElse(null);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asText() : null;
    }

    /**
     * Итог сверки с docproc.
     *
     * @param documents документов в docproc
     * @param updated обновлено
     * @param removed снято
     */
    public record Reconciled(int documents, int updated, int removed) {
    }
}
