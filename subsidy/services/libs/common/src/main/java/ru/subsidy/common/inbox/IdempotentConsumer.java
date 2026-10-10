package ru.subsidy.common.inbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Идемпотентность потребителя: eventId пишется в processed_events в той же транзакции, что и обработка.
 */
public class IdempotentConsumer {

    private final JdbcClient jdbc;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param jdbc JDBC
     * @param clock часы
     */
    public IdempotentConsumer(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Отмечает событие обработанным.
     *
     * @param eventId id события
     * @return true, если событие новое и его нужно обработать
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId) {
        return jdbc.sql("insert into processed_events(event_id, processed_at) values (?, ?) on conflict do nothing")
                .params(eventId, Timestamp.from(clock.instant()))
                .update() == 1;
    }
}
