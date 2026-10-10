package ru.subsidy.rules.facts;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.rules.history.OrgHistoryService;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.jooq.tables.records.PackageViewRecord;
import ru.subsidy.rules.recheck.RecheckRequests;

/**
 * Правка значения поля специалистом (FR-47): данные, не вердикт; перепроверка и история в той же транзакции.
 */
@Service
public class FieldOverrideService {

    private final DSLContext dsl;
    private final RecheckRequests recheck;
    private final OrgHistoryService history;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param recheck перепроверки
     * @param history история
     * @param clock часы
     */
    public FieldOverrideService(DSLContext dsl, RecheckRequests recheck, OrgHistoryService history, Clock clock) {
        this.dsl = dsl;
        this.recheck = recheck;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Сохраняет правку и ставит пакет на перепроверку.
     *
     * @param packageId пакет
     * @param documentId документ
     * @param field поле
     * @param value новое значение (null — очистить)
     * @param reason причина
     * @param userId специалист
     * @throws DomainException PACKAGE_NOT_FOUND, DOCUMENT_NOT_FOUND, PACKAGE_DECIDED
     */
    @Transactional
    public void override(UUID packageId, UUID documentId, String field, String value, String reason, String userId) {
        PackageViewRecord view = dsl.selectFrom(Tables.PACKAGE_VIEW).where(Tables.PACKAGE_VIEW.PACKAGE_ID.eq(packageId))
                .fetchOne();
        if (view == null) {
            throw new DomainException(ErrorCode.PACKAGE_NOT_FOUND, "Пакет не найден");
        }
        if ("DECIDED".equals(view.getStatus())) {
            throw new DomainException(ErrorCode.PACKAGE_DECIDED, "По пакету уже принято решение");
        }
        boolean belongs = dsl.fetchExists(Tables.PACKAGE_DOCUMENTS, Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.eq(documentId)
                .and(Tables.PACKAGE_DOCUMENTS.PACKAGE_ID.eq(packageId)).and(Tables.PACKAGE_DOCUMENTS.REMOVED.isFalse()));
        if (!belongs) {
            throw new DomainException(ErrorCode.DOCUMENT_NOT_FOUND, "Документ не найден в пакете");
        }
        dsl.insertInto(Tables.FIELD_OVERRIDES)
                .set(Tables.FIELD_OVERRIDES.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.FIELD_OVERRIDES.PACKAGE_ID, packageId)
                .set(Tables.FIELD_OVERRIDES.DOCUMENT_ID, documentId)
                .set(Tables.FIELD_OVERRIDES.FIELD, field)
                .set(Tables.FIELD_OVERRIDES.VALUE, value)
                .set(Tables.FIELD_OVERRIDES.REASON, reason)
                .set(Tables.FIELD_OVERRIDES.CREATED_BY, userId)
                .set(Tables.FIELD_OVERRIDES.CREATED_AT, OffsetDateTime.now(clock))
                .execute();
        Map<String, Object> details = new HashMap<>();
        details.put("documentId", documentId.toString());
        details.put("field", field);
        details.put("reason", reason);
        history.append(view.getInn(), userId, "FIELD_CHANGED", packageId, details);
        recheck.request(packageId);
    }
}
