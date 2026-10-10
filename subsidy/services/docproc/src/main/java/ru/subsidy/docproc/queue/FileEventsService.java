package ru.subsidy.docproc.queue;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.inbox.IdempotentConsumer;
import ru.subsidy.common.outbox.OutboxWriter;
import ru.subsidy.docproc.jooq.Tables;

/**
 * События package.file.v1 → задания в jobs (идемпотентно по eventId).
 */
@Service
public class FileEventsService {

    private static final String FILE_ID = "fileId";
    private static final String PACKAGE_ID = "packageId";
    private static final String VERSION = "version";

    private final JobQueue queue;
    private final IdempotentConsumer inbox;
    private final OutboxWriter outbox;
    private final DSLContext dsl;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param queue очередь
     * @param inbox идемпотентность
     * @param outbox outbox
     * @param dsl jOOQ
     * @param clock часы
     */
    public FileEventsService(JobQueue queue, IdempotentConsumer inbox, OutboxWriter outbox, DSLContext dsl,
                             Clock clock) {
        this.queue = queue;
        this.inbox = inbox;
        this.outbox = outbox;
        this.dsl = dsl;
        this.clock = clock;
    }

    /**
     * Обрабатывает событие файла.
     *
     * @param eventId id события
     * @param type тип
     * @param payload данные (имени файла в них нет)
     */
    @Transactional
    public void handle(UUID eventId, String type, JsonNode payload) {
        if (!inbox.firstTime(eventId)) {
            return;
        }
        UUID fileId = UUID.fromString(payload.path(FILE_ID).asText());
        if ("FileRemoved".equals(type)) {
            removed(fileId);
        } else if ("FileReceived".equals(type) || "FileReplaced".equals(type)) {
            received(fileId, payload);
        }
    }

    private void received(UUID fileId, JsonNode payload) {
        UUID packageId = UUID.fromString(payload.path(PACKAGE_ID).asText());
        int version = payload.path(VERSION).asInt(1);
        if (queue.enqueue(fileId, packageId, version, payload.path("mime").asText())) {
            Map<String, Object> event = new HashMap<>();
            event.put(FILE_ID, fileId.toString());
            event.put(PACKAGE_ID, packageId.toString());
            event.put(VERSION, version);
            event.put("stage", Stage.QUEUED);
            outbox.append(Topics.DOCPROC_DOCUMENT_V1, "StageChanged", fileId.toString(), event);
        }
    }

    private void removed(UUID fileId) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.update(Tables.JOBS).set(Tables.JOBS.STAGE, Stage.REMOVED).set(Tables.JOBS.UPDATED_AT, now)
                .set(Tables.JOBS.LEASE_UNTIL, (OffsetDateTime) null)
                .where(Tables.JOBS.FILE_ID.eq(fileId)).execute();
        dsl.update(Tables.DOCUMENTS).set(Tables.DOCUMENTS.REMOVED, true).set(Tables.DOCUMENTS.UPDATED_AT, now)
                .where(Tables.DOCUMENTS.FILE_ID.eq(fileId)).execute();
    }
}
