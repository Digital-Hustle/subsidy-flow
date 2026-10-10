package ru.subsidy.rules.recheck;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.jooq.Tables;

/**
 * Debounce перепроверок в таблице recheck_requests (не в памяти): каждое событие сдвигает срок.
 */
@Component
public class RecheckRequests {

    private final DSLContext dsl;
    private final Clock clock;
    private final RulesProperties props;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     * @param props настройки
     */
    public RecheckRequests(DSLContext dsl, Clock clock, RulesProperties props) {
        this.dsl = dsl;
        this.clock = clock;
        this.props = props;
    }

    /**
     * Просит перепроверить пакет через debounce.
     *
     * @param packageId пакет
     */
    public void request(UUID packageId) {
        requestAfter(packageId, props.debounce());
    }

    /**
     * Просит перепроверить пакет через заданную паузу.
     *
     * @param packageId пакет
     * @param delay пауза
     */
    public void requestAfter(UUID packageId, Duration delay) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.RECHECK_REQUESTS)
                .set(Tables.RECHECK_REQUESTS.PACKAGE_ID, packageId)
                .set(Tables.RECHECK_REQUESTS.DUE_AT, now.plus(delay))
                .set(Tables.RECHECK_REQUESTS.REQUESTED_AT, now)
                .onConflict(Tables.RECHECK_REQUESTS.PACKAGE_ID).doUpdate()
                .set(Tables.RECHECK_REQUESTS.DUE_AT, now.plus(delay))
                .set(Tables.RECHECK_REQUESTS.REQUESTED_AT, now)
                .execute();
    }

    /**
     * Забирает один созревший запрос (SKIP LOCKED) и удаляет его — в транзакции вызывающего.
     *
     * @return пакет или пусто
     */
    public Optional<UUID> claimDue() {
        Optional<UUID> due = dsl.select(Tables.RECHECK_REQUESTS.PACKAGE_ID).from(Tables.RECHECK_REQUESTS)
                .where(Tables.RECHECK_REQUESTS.DUE_AT.le(OffsetDateTime.now(clock)))
                .orderBy(Tables.RECHECK_REQUESTS.DUE_AT).limit(1).forUpdate().skipLocked()
                .fetchOptional(Tables.RECHECK_REQUESTS.PACKAGE_ID);
        due.ifPresent(id -> dsl.deleteFrom(Tables.RECHECK_REQUESTS).where(Tables.RECHECK_REQUESTS.PACKAGE_ID.eq(id))
                .execute());
        return due;
    }
}
