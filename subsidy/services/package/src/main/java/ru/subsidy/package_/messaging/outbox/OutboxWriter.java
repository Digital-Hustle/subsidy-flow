package ru.subsidy.package_.messaging.outbox;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.event.EventEnvelope;
import ru.subsidy.package_.jooq.Tables;

/**
 * Пишет событие в outbox в текущей транзакции; отправка в Kafka — отдельный relay.
 */
@Component
public class OutboxWriter {

    private final DSLContext dsl;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param mapper JSON
     * @param clock часы
     */
    public OutboxWriter(DSLContext dsl, ObjectMapper mapper, Clock clock) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Добавляет событие.
     *
     * @param topic топик
     * @param type тип события
     * @param aggregateId ключ сообщения
     * @param payload данные (без ПДн и имён файлов)
     * @return id события
     * @throws IllegalStateException если payload не сериализуется
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String topic, String type, String aggregateId, Map<String, Object> payload) {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        OffsetDateTime now = OffsetDateTime.now(clock);
        EventEnvelope envelope = new EventEnvelope(eventId.toString(), type, now.toInstant(), aggregateId, null,
                payload);
        try {
            dsl.insertInto(Tables.OUTBOX)
                    .set(Tables.OUTBOX.ID, eventId)
                    .set(Tables.OUTBOX.TOPIC, topic)
                    .set(Tables.OUTBOX.MESSAGE_KEY, aggregateId)
                    .set(Tables.OUTBOX.PAYLOAD, JSONB.valueOf(mapper.writeValueAsString(envelope)))
                    .set(Tables.OUTBOX.CREATED_AT, now)
                    .execute();
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Событие не сериализуется", failure);
        }
        return eventId;
    }
}
