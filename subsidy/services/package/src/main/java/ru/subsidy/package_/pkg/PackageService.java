package ru.subsidy.package_.pkg;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.messaging.outbox.OutboxWriter;
import ru.subsidy.package_.organization.OrganizationService;
import ru.subsidy.package_.organization.OrganizationService.Organization;

/**
 * Пакет документов организации на программу субсидирования (FR-01).
 */
@Service
public class PackageService {

    /** Статус нового пакета. */
    public static final String COLLECTING = "COLLECTING";

    private final DSLContext dsl;
    private final Clock clock;
    private final OrganizationService organizations;
    private final List<String> programs;
    private final OutboxWriter outbox;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     * @param organizations организации
     * @param programs доступные программы
     * @param outbox outbox событий
     */
    public PackageService(DSLContext dsl, Clock clock, OrganizationService organizations,
                          @Value("${subsidy.programs:INNOVATION,BEACH,MODERNIZATION}") List<String> programs, OutboxWriter outbox) {
        this.dsl = dsl;
        this.clock = clock;
        this.organizations = organizations;
        this.programs = List.copyOf(programs);
        this.outbox = outbox;
    }

    /**
     * Создаёт пакет; повтор с тем же Idempotency-Key возвращает тот же пакет.
     *
     * @param orgInn ИНН заявителя
     * @param programCode код программы
     * @param idempotencyKey ключ идемпотентности или null
     * @param userId кто создаёт (из JWT)
     * @return пакет
     * @throws DomainException PROGRAM_NOT_FOUND или INN_INVALID
     */
    @Transactional
    public PackageView create(String orgInn, String programCode, String idempotencyKey, String userId) {
        if (!programs.contains(programCode)) {
            throw new DomainException(ErrorCode.PROGRAM_NOT_FOUND, "Программа не найдена: " + programCode);
        }
        if (idempotencyKey != null) {
            Optional<UUID> existing = dsl.select(Tables.PACKAGES.ID).from(Tables.PACKAGES)
                    .where(Tables.PACKAGES.IDEMPOTENCY_KEY.eq(idempotencyKey)).fetchOptional(Tables.PACKAGES.ID);
            if (existing.isPresent()) {
                return get(existing.get());
            }
        }
        Organization org = organizations.getOrCreate(orgInn, userId);
        UUID newId = UuidCreator.getTimeOrderedEpoch();
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.PACKAGES)
                .set(Tables.PACKAGES.ID, newId)
                .set(Tables.PACKAGES.ORG_ID, org.id())
                .set(Tables.PACKAGES.PROGRAM_CODE, programCode)
                .set(Tables.PACKAGES.STATUS, COLLECTING)
                .set(Tables.PACKAGES.IDEMPOTENCY_KEY, idempotencyKey)
                .set(Tables.PACKAGES.CREATED_BY, userId)
                .set(Tables.PACKAGES.CREATED_AT, now)
                .set(Tables.PACKAGES.UPDATED_AT, now)
                .execute();
        outbox.append(Topics.PACKAGE_PACKAGE_V1, "PackageCreated", newId.toString(), Map.of(
                "packageId", newId.toString(), "orgId", org.id().toString(), "inn", org.inn(),
                "programCode", programCode, "createdBy", userId));
        return get(newId);
    }

    /**
     * Пакет по id.
     *
     * @param packageId id пакета
     * @return пакет
     */
    @Transactional(readOnly = true)
    public PackageView get(UUID packageId) {
        return dsl.select(Tables.PACKAGES.ID, Tables.PACKAGES.STATUS, Tables.PACKAGES.PROGRAM_CODE, Tables.PACKAGES.UPDATED_AT,
                        Tables.ORGANIZATIONS.INN, Tables.ORGANIZATIONS.NAME)
                .from(Tables.PACKAGES).join(Tables.ORGANIZATIONS).on(Tables.ORGANIZATIONS.ID.eq(Tables.PACKAGES.ORG_ID))
                .where(Tables.PACKAGES.ID.eq(packageId))
                .fetchOptional(PackageService::toView)
                .orElseThrow(() -> new NotFoundException("Пакет не найден"));
    }

    private static PackageView toView(Record rec) {
        return new PackageView(rec.get(Tables.PACKAGES.ID), rec.get(Tables.PACKAGES.STATUS), rec.get(Tables.PACKAGES.PROGRAM_CODE),
                rec.get(Tables.ORGANIZATIONS.INN), rec.get(Tables.ORGANIZATIONS.NAME), rec.get(Tables.PACKAGES.UPDATED_AT));
    }

    /**
     * Представление пакета.
     *
     * @param id id
     * @param status статус
     * @param programCode программа
     * @param orgInn ИНН
     * @param orgName наименование
     * @param updatedAt изменён
     */
    public record PackageView(UUID id, String status, String programCode, String orgInn, String orgName,
                              OffsetDateTime updatedAt) {
    }
}
