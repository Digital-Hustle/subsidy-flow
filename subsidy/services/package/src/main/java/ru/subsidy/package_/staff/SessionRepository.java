package ru.subsidy.package_.staff;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.jooq.tables.StaffSessions;

/**
 * Сессии персонала (refresh хранится только SHA-256).
 */
@Repository
public class SessionRepository {

    private static final StaffSessions SESSIONS = Tables.STAFF_SESSIONS;

    private final DSLContext dsl;

    public SessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Новая сессия.
     *
     * @param id          id (claim {@code sid})
     * @param staffId     сотрудник
     * @param refreshHash SHA-256 refresh-токена
     * @param moment      создана
     * @param expiresAt   истекает
     */
    public void insert(UUID id, UUID staffId, String refreshHash, Instant moment, Instant expiresAt) {
        dsl.insertInto(SESSIONS)
                .set(SESSIONS.ID, id)
                .set(SESSIONS.STAFF_ID, staffId)
                .set(SESSIONS.REFRESH_HASH, refreshHash)
                .set(SESSIONS.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .set(SESSIONS.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Действующая сессия по refresh, строка заблокирована (ротация при гонке).
     *
     * @param refreshHash SHA-256 refresh-токена
     * @param now         момент
     * @return сессия
     */
    public Optional<SessionRow> lockByRefresh(String refreshHash, Instant now) {
        return dsl.selectFrom(SESSIONS)
                .where(SESSIONS.REFRESH_HASH.eq(refreshHash).and(SESSIONS.REVOKED_AT.isNull())
                        .and(SESSIONS.EXPIRES_AT.gt(now.atOffset(ZoneOffset.UTC))))
                .forUpdate()
                .fetchOptional(row -> new SessionRow(row.getId(), row.getStaffId(), row.getExpiresAt().toInstant()));
    }

    /**
     * Новый refresh у сессии.
     *
     * @param id          сессия
     * @param refreshHash SHA-256 нового refresh-токена
     */
    public void rotate(UUID id, String refreshHash) {
        dsl.update(SESSIONS).set(SESSIONS.REFRESH_HASH, refreshHash).where(SESSIONS.ID.eq(id)).execute();
    }

    /**
     * Закрывает сессию.
     *
     * @param id     сессия
     * @param moment момент
     */
    public void revoke(UUID id, Instant moment) {
        dsl.update(SESSIONS).set(SESSIONS.REVOKED_AT, moment.atOffset(ZoneOffset.UTC))
                .where(SESSIONS.ID.eq(id).and(SESSIONS.REVOKED_AT.isNull())).execute();
    }

    /**
     * Закрывает все сессии сотрудника.
     *
     * @param staffId сотрудник
     * @param moment  момент
     */
    public void revokeAll(UUID staffId, Instant moment) {
        dsl.update(SESSIONS).set(SESSIONS.REVOKED_AT, moment.atOffset(ZoneOffset.UTC))
                .where(SESSIONS.STAFF_ID.eq(staffId).and(SESSIONS.REVOKED_AT.isNull())).execute();
    }

    /**
     * Строка сессии.
     *
     * @param id        сессия
     * @param staffId   сотрудник
     * @param expiresAt истекает
     */
    public record SessionRow(UUID id, UUID staffId, Instant expiresAt) {
    }
}
