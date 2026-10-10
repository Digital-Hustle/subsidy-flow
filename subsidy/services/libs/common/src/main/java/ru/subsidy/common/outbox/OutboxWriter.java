package ru.subsidy.common.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.event.EventEnvelope;

/**
 * Transactional outbox: событие пишется в таблицу outbox в той же транзакции, что и изменение данных.
 */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param jdbc JDBC
     * @param json JSON
     * @param clock часы
     */
    public OutboxWriter(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Добавляет событие в outbox (только внутри текущей транзакции).
     *
     * @param topic топик
     * @param type тип события
     * @param aggregateId ключ сообщения
     * @param payload данные (без ПДн и имён файлов)
     * @return id события
     * @throws IllegalStateException payload не сериализуется
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String topic, String type, String aggregateId, Map<String, Object> payload) {
        final UUID eventId = UuidCreator.getTimeOrderedEpoch();
        final EventEnvelope envelope = new EventEnvelope(eventId.toString(), type, clock.instant(), aggregateId,
                null, payload);
        String body;
        try {
            body = json.writeValueAsString(envelope);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Событие не сериализуется", failure);
        }
        jdbc.sql("insert into outbox(id, topic, message_key, payload, created_at) values (?, ?, ?, ?::jsonb, ?)")
                .params(eventId, topic, aggregateId, body, Timestamp.from(clock.instant()))
                .update();
        return eventId;
    }
}
