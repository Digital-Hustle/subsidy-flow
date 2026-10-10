package ru.subsidy.package_.staff;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.jooq.tables.StaffUsers;

/**
 * Сотрудники.
 */
@Repository
public class StaffRepository {

    private static final StaffUsers STAFF = Tables.STAFF_USERS;

    private final DSLContext dsl;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     */
    public StaffRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сериализует изменения ролей и статусов: проверка «последний старший» не обгоняет параллельную правку.
     */
    public void lockSeniors() {
        dsl.execute("select pg_advisory_xact_lock(hashtext('staff:seniors'))");
    }

    /**
     * Активных старших специалистов.
     *
     * @return число
     */
    public int activeSeniors() {
        return dsl.fetchCount(STAFF, STAFF.ROLE.eq(StaffRole.SENIOR_SPECIALIST.name()).and(STAFF.STATUS.ne(StaffStatus.DISABLED.name())));
    }

    /**
     * Есть ли хоть один сотрудник, кроме демо.
     *
     * @return есть
     */
    public boolean anyReal() {
        return dsl.fetchExists(STAFF, STAFF.DEMO.isFalse());
    }

    /**
     * Отключает демо-сотрудников (боевой режим стенда).
     */
    public void disableDemo() {
        dsl.update(STAFF).set(STAFF.STATUS, StaffStatus.DISABLED.name()).where(STAFF.DEMO.isTrue()).execute();
    }

    /**
     * Все сотрудники: сначала активные, по имени.
     *
     * @return список
     */
    public List<StaffUser> list() {
        return dsl.selectFrom(STAFF).orderBy(DSL.when(STAFF.STATUS.eq(StaffStatus.DISABLED.name()), 1).otherwise(0), STAFF.NAME)
                .fetch(StaffRepository::toUser);
    }

    /**
     * Демо-сотрудники (только демо-стенд).
     *
     * @return список
     */
    public List<StaffUser> demo() {
        return dsl.selectFrom(STAFF).where(STAFF.DEMO.isTrue().and(STAFF.STATUS.ne(StaffStatus.DISABLED.name())))
                .orderBy(STAFF.ROLE, STAFF.NAME).fetch(StaffRepository::toUser);
    }

    /**
     * Сотрудник по почте, строка заблокирована.
     *
     * @param email почта
     * @return сотрудник
     */
    public Optional<StaffUser> lockByEmail(String email) {
        return dsl.selectFrom(STAFF).where(DSL.lower(STAFF.EMAIL).eq(email.toLowerCase(Locale.ROOT))).forUpdate()
                .fetchOptional(StaffRepository::toUser);
    }

    /**
     * Сотрудник по id, строка заблокирована.
     *
     * @param id идентификатор
     * @return сотрудник
     */
    public Optional<StaffUser> lock(UUID id) {
        return dsl.selectFrom(STAFF).where(STAFF.ID.eq(id)).forUpdate().fetchOptional(StaffRepository::toUser);
    }

    /**
     * Сотрудник по id.
     *
     * @param id идентификатор
     * @return сотрудник
     */
    public Optional<StaffUser> find(UUID id) {
        return dsl.selectFrom(STAFF).where(STAFF.ID.eq(id)).fetchOptional(StaffRepository::toUser);
    }

    /**
     * Добавляет сотрудника.
     *
     * @param user   сотрудник
     * @param moment момент
     * @return false — почта уже занята
     */
    public boolean insert(StaffUser user, Instant moment) {
        return dsl.insertInto(STAFF)
                .set(STAFF.ID, user.id())
                .set(STAFF.NAME, user.name())
                .set(STAFF.EMAIL, user.email())
                .set(STAFF.ROLE, user.role().name())
                .set(STAFF.STATUS, user.status().name())
                .set(STAFF.DEMO, user.demo())
                .set(STAFF.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }

    /**
     * Меняет роль.
     *
     * @param id   сотрудник
     * @param role роль
     */
    public void setRole(UUID id, StaffRole role) {
        dsl.update(STAFF).set(STAFF.ROLE, role.name()).where(STAFF.ID.eq(id)).execute();
    }

    /**
     * Меняет статус.
     *
     * @param id     сотрудник
     * @param status статус
     */
    public void setStatus(UUID id, StaffStatus status) {
        dsl.update(STAFF).set(STAFF.STATUS, status.name()).where(STAFF.ID.eq(id)).execute();
    }

    /**
     * Отмечает вход или продление.
     *
     * @param id     сотрудник
     * @param moment момент
     */
    public void touch(UUID id, Instant moment) {
        dsl.update(STAFF).set(STAFF.LAST_SEEN_AT, moment.atOffset(ZoneOffset.UTC)).where(STAFF.ID.eq(id)).execute();
    }

    private static StaffUser toUser(Record row) {
        OffsetDateTime seen = row.get(STAFF.LAST_SEEN_AT);
        return new StaffUser(row.get(STAFF.ID), row.get(STAFF.NAME), row.get(STAFF.EMAIL), StaffRole.valueOf(row.get(STAFF.ROLE)),
                StaffStatus.valueOf(row.get(STAFF.STATUS)), Boolean.TRUE.equals(row.get(STAFF.DEMO)),
                seen == null ? null : seen.toInstant());
    }
}
