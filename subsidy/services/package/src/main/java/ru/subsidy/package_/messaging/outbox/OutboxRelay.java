package ru.subsidy.package_.messaging.outbox;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.subsidy.package_.jooq.Tables;

/**
 * Перекладывает события из outbox в Kafka пачками, по порядку, с SKIP LOCKED.
 * Kafka недоступна — события остаются в таблице и уйдут позже.
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final DSLContext dsl;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param kafka продюсер
     * @param transactions транзакции
     * @param clock часы
     */
    public OutboxRelay(DSLContext dsl, KafkaTemplate<String, String> kafka, TransactionTemplate transactions,
                       Clock clock) {
        this.dsl = dsl;
        this.kafka = kafka;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Один проход relay: шлём, пока есть полные пачки. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:300}")
    public void relay() {
        Integer sent;
        do {
            sent = transactions.execute(status -> sendBatch());
        } while (sent != null && sent == BATCH);
    }

    private int sendBatch() {
        Result<Record> rows = dsl.select(Tables.OUTBOX.fields()).from(Tables.OUTBOX)
                .where(Tables.OUTBOX.PUBLISHED_AT.isNull())
                .orderBy(Tables.OUTBOX.CREATED_AT, Tables.OUTBOX.ID)
                .limit(BATCH)
                .forUpdate().skipLocked()
                .fetch();
        int sent = 0;
        for (Record row : rows) {
            if (!send(row)) {
                break;
            }
            dsl.update(Tables.OUTBOX).set(Tables.OUTBOX.PUBLISHED_AT, OffsetDateTime.now(clock))
                    .where(Tables.OUTBOX.ID.eq(row.get(Tables.OUTBOX.ID))).execute();
            sent++;
        }
        return sent;
    }

    private boolean send(Record row) {
        String topic = row.get(Tables.OUTBOX.TOPIC);
        try {
            kafka.send(topic, row.get(Tables.OUTBOX.MESSAGE_KEY), row.get(Tables.OUTBOX.PAYLOAD).data())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException failure) {
            LOG.warn("outbox send failed topic={} id={}: {}", topic, row.get(Tables.OUTBOX.ID), failure.getMessage());
            return false;
        }
    }
}
