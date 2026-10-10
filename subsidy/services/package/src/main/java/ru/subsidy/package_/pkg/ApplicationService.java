package ru.subsidy.package_.pkg;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.messaging.outbox.OutboxWriter;
import ru.subsidy.package_.organization.OrganizationService;

/**
 * Заявка, неизменяемая история её ревизий и событие для перепроверки (FR-01/47).
 */
@Service
public class ApplicationService {

    private static final String PACKAGE_NOT_FOUND = "Пакет не найден";
    private final DSLContext dsl;
    private final ObjectMapper json;
    private final Clock clock;
    private final OrganizationService organizations;
    private final OutboxWriter outbox;

    /**
     * Конструктор.
     *
     * @param dsl БД
     * @param json JSON
     * @param clock часы
     * @param organizations организации
     * @param outbox события
     */
    public ApplicationService(DSLContext dsl, ObjectMapper json, Clock clock,
                               OrganizationService organizations, OutboxWriter outbox) {
        this.dsl = dsl;
        this.json = json;
        this.clock = clock;
        this.organizations = organizations;
        this.outbox = outbox;
    }

    /**
     * История, начиная с последней ревизии.
     *
     * @param packageId пакет
     * @return изменения
     * @throws NotFoundException пакет не существует
     */
    @Transactional(readOnly = true)
    public List<Change> history(UUID packageId) {
        if (!dsl.fetchExists(Tables.PACKAGES, Tables.PACKAGES.ID.eq(packageId))) {
            throw new NotFoundException(PACKAGE_NOT_FOUND);
        }
        return dsl.selectFrom(Tables.APPLICATION_CHANGES)
                .where(Tables.APPLICATION_CHANGES.PACKAGE_ID.eq(packageId))
                .orderBy(Tables.APPLICATION_CHANGES.REVISION.desc())
                .fetch(row -> new Change(row.getRevision(), row.getChangedBy(), row.getChangedAt(),
                        read(row.getBeforeData()), read(row.getAfterData())));
    }

    /**
     * Полная замена заявки с блокировкой пакета и проверкой версии.
     *
     * @param packageId пакет
     * @param expectedRevision версия, которую видел клиент
     * @param application новая заявка
     * @param userId автор из JWT
     * @return новая версия
     * @throws NotFoundException пакет не существует
     * @throws DomainException решённый пакет или конфликт версии
     */
    @Transactional
    public Revision update(UUID packageId, int expectedRevision, ApplicationData application, String userId) {
        var row = dsl.selectFrom(Tables.PACKAGES).where(Tables.PACKAGES.ID.eq(packageId)).forUpdate()
                .fetchOptional().orElseThrow(() -> new NotFoundException(PACKAGE_NOT_FOUND));
        if ("DECIDED".equals(row.getStatus())) {
            throw new DomainException(ErrorCode.PACKAGE_DECIDED, "По пакету уже принято решение");
        }
        if (row.getApplicationRevision() != expectedRevision) {
            throw new DomainException(ErrorCode.CONFLICT, "Заявку уже изменили. Обновите страницу и повторите правку");
        }
        JSONB after = write(application);
        if (Objects.equals(row.getApplication(), after)) {
            return new Revision(packageId, expectedRevision, application);
        }
        UUID orgId = organizations.getOrCreate(application.orgInn(), userId).id();
        int revision = expectedRevision + 1;
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.update(Tables.PACKAGES)
                .set(Tables.PACKAGES.ORG_ID, orgId)
                .set(Tables.PACKAGES.APPLICATION, after)
                .set(Tables.PACKAGES.APPLICATION_REVISION, revision)
                .set(Tables.PACKAGES.REQUESTED_AMOUNT, application.requestedAmount())
                .set(Tables.PACKAGES.STATUS, dsl.fetchExists(Tables.FILES, Tables.FILES.PACKAGE_ID.eq(packageId)
                        .and(Tables.FILES.DELETED_AT.isNull())) ? "PROCESSING" : "COLLECTING")
                .set(Tables.PACKAGES.PROTOCOL_STATUS, (String) null)
                .set(Tables.PACKAGES.DECISION, (String) null)
                .set(Tables.PACKAGES.DECISION_COMMENT, (String) null)
                .set(Tables.PACKAGES.DECIDED_BY, (String) null)
                .set(Tables.PACKAGES.DECIDED_AT, (OffsetDateTime) null)
                .set(Tables.PACKAGES.COMPLETENESS, (Integer) null)
                .set(Tables.PACKAGES.FAILED, 0).set(Tables.PACKAGES.WARNINGS, 0).set(Tables.PACKAGES.NEEDS_REVIEW, 0)
                .set(Tables.PACKAGES.UPDATED_AT, now)
                .where(Tables.PACKAGES.ID.eq(packageId)).execute();
        dsl.insertInto(Tables.APPLICATION_CHANGES)
                .set(Tables.APPLICATION_CHANGES.PACKAGE_ID, packageId)
                .set(Tables.APPLICATION_CHANGES.REVISION, revision)
                .set(Tables.APPLICATION_CHANGES.BEFORE_DATA, row.getApplication())
                .set(Tables.APPLICATION_CHANGES.AFTER_DATA, after)
                .set(Tables.APPLICATION_CHANGES.CHANGED_BY, userId)
                .set(Tables.APPLICATION_CHANGES.CHANGED_AT, now).execute();
        outbox.append(Topics.PACKAGE_PACKAGE_V1, "PackageApplicationChanged", packageId.toString(), Map.of(
                "packageId", packageId.toString(), "orgId", orgId.toString(), "inn", application.orgInn(),
                "programCode", row.getProgramCode(), "applicationRevision", revision,
                "application", json.valueToTree(application), "changedBy", userId, "changedAt", now));
        return new Revision(packageId, revision, application);
    }

    /**
     * Читает сохранённую заявку или null для старого черновика.
     *
     * @param data JSONB
     * @return заявка
     * @throws IllegalStateException повреждённые данные
     */
    public ApplicationData read(JSONB data) {
        try {
            return data == null ? null : json.readValue(data.data(), ApplicationData.class);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("Не удалось прочитать заявку", broken);
        }
    }

    private JSONB write(ApplicationData application) {
        try {
            return JSONB.valueOf(json.writeValueAsString(application));
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("Не удалось сохранить заявку", broken);
        }
    }

    /**
     * Текущая ревизия.
     *
     * @param packageId пакет
     * @param applicationRevision версия
     * @param application заявка
     */
    public record Revision(UUID packageId, int applicationRevision, ApplicationData application) {
    }

    /**
     * Одно изменение с двумя снимками.
     *
     * @param revision версия после правки
     * @param changedBy кто изменил
     * @param changedAt когда
     * @param before до
     * @param after после
     */
    public record Change(int revision, String changedBy, OffsetDateTime changedAt,
                          ApplicationData before, ApplicationData after) {
    }
}
