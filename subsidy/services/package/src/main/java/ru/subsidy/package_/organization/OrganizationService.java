package ru.subsidy.package_.organization;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.package_.jooq.Tables;

/**
 * Организации-заявители по ИНН (FR-01).
 */
@Service
public class OrganizationService {

    private final DSLContext dsl;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     */
    public OrganizationService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    /**
     * Ищет организацию по ИНН.
     *
     * @param inn ИНН
     * @return организация, если есть
     */
    @Transactional(readOnly = true)
    public Optional<Organization> findByInn(String inn) {
        requireValid(inn);
        return dsl.selectFrom(Tables.ORGANIZATIONS).where(Tables.ORGANIZATIONS.INN.eq(inn))
                .fetchOptional(rec -> new Organization(rec.getId(), rec.getInn(), rec.getName()));
    }

    /**
     * Создаёт организацию или возвращает уже существующую (идемпотентно по ИНН).
     *
     * @param inn ИНН
     * @param userId кто создаёт (из JWT)
     * @return организация
     */
    @Transactional
    public Organization getOrCreate(String inn, String userId) {
        requireValid(inn);
        UUID newId = UuidCreator.getTimeOrderedEpoch();
        dsl.insertInto(Tables.ORGANIZATIONS)
                .set(Tables.ORGANIZATIONS.ID, newId)
                .set(Tables.ORGANIZATIONS.INN, inn)
                .set(Tables.ORGANIZATIONS.CREATED_BY, userId)
                .set(Tables.ORGANIZATIONS.CREATED_AT, OffsetDateTime.now(clock))
                .onConflict(Tables.ORGANIZATIONS.INN).doNothing()
                .execute();
        return dsl.selectFrom(Tables.ORGANIZATIONS).where(Tables.ORGANIZATIONS.INN.eq(inn))
                .fetchSingle(rec -> new Organization(rec.getId(), rec.getInn(), rec.getName()));
    }

    private static void requireValid(String inn) {
        if (!InnValidator.isValid(inn)) {
            throw new DomainException(ErrorCode.INN_INVALID, "ИНН не прошёл проверку контрольной суммы");
        }
    }

    /**
     * Организация.
     *
     * @param id идентификатор
     * @param inn ИНН
     * @param name наименование (заполняется из выписки ЕГРЮЛ позже)
     */
    public record Organization(UUID id, String inn, String name) {
    }
}
