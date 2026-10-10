package ru.subsidy.package_.pkg;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.messaging.outbox.OutboxWriter;

/**
 * Решение старшего специалиста по пакету: одобрить, отказать или вернуть на доработку.
 */
@Service
public class DecisionService {

    /** Статус пакета с принятым решением. */
    public static final String DECIDED = "DECIDED";
    /** Решение «вернуть на доработку». */
    public static final String RETURN = "RETURN";
    /** Решение «отказать». */
    public static final String REJECT = "REJECT";

    private final DSLContext dsl;
    private final Clock clock;
    private final OutboxWriter outbox;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     * @param outbox outbox
     */
    public DecisionService(DSLContext dsl, Clock clock, OutboxWriter outbox) {
        this.dsl = dsl;
        this.clock = clock;
        this.outbox = outbox;
    }

    /**
     * Фиксирует решение; RETURN возвращает пакет в сбор, чтобы можно было догрузить файлы.
     *
     * @param packageId пакет
     * @param decision APPROVE, REJECT или RETURN
     * @param comment комментарий (обязателен для REJECT и RETURN)
     * @param userId кто решает (из JWT)
     * @return результат
     * @throws DomainException VALIDATION_ERROR, PACKAGE_DECIDED, PROTOCOL_NOT_FOUND
     */
    @Transactional
    public DecisionResult decide(UUID packageId, String decision, String comment, String userId) {
        final boolean needsComment = RETURN.equals(decision) || REJECT.equals(decision);
        if (needsComment && (comment == null || comment.isBlank())) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Для отказа и возврата нужен комментарий");
        }
        Record row = dsl.select(Tables.PACKAGES.STATUS, Tables.PACKAGES.PROTOCOL_STATUS, Tables.PACKAGES.PROGRAM_CODE,
                        Tables.PACKAGES.ORG_ID, Tables.ORGANIZATIONS.INN)
                .from(Tables.PACKAGES).join(Tables.ORGANIZATIONS).on(Tables.ORGANIZATIONS.ID.eq(Tables.PACKAGES.ORG_ID))
                .where(Tables.PACKAGES.ID.eq(packageId))
                .forUpdate().of(Tables.PACKAGES)
                .fetchOptional()
                .orElseThrow(() -> new NotFoundException("Пакет не найден"));
        if (DECIDED.equals(row.get(Tables.PACKAGES.STATUS))) {
            throw new DomainException(ErrorCode.PACKAGE_DECIDED, "По пакету уже принято решение");
        }
        if (row.get(Tables.PACKAGES.PROTOCOL_STATUS) == null) {
            throw new DomainException(ErrorCode.PROTOCOL_NOT_FOUND, "Решение возможно только после проверки пакета");
        }
        final String status = RETURN.equals(decision) ? PackageService.COLLECTING : DECIDED;
        final OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.update(Tables.PACKAGES)
                .set(Tables.PACKAGES.STATUS, status)
                .set(Tables.PACKAGES.DECISION, decision)
                .set(Tables.PACKAGES.DECISION_COMMENT, comment)
                .set(Tables.PACKAGES.DECIDED_BY, userId)
                .set(Tables.PACKAGES.DECIDED_AT, now)
                .set(Tables.PACKAGES.UPDATED_AT, now)
                .where(Tables.PACKAGES.ID.eq(packageId))
                .execute();
        outbox.append(Topics.PACKAGE_PACKAGE_V1, "PackageDecided", packageId.toString(), Map.of(
                "packageId", packageId.toString(),
                "orgId", row.get(Tables.PACKAGES.ORG_ID).toString(),
                "inn", row.get(Tables.ORGANIZATIONS.INN),
                "programCode", row.get(Tables.PACKAGES.PROGRAM_CODE),
                "decision", decision,
                "decidedBy", userId));
        return new DecisionResult(packageId, status, decision, userId, now);
    }

    /**
     * Результат решения.
     *
     * @param packageId пакет
     * @param status новый статус пакета
     * @param decision решение
     * @param decidedBy кто решил
     * @param decidedAt когда
     */
    public record DecisionResult(UUID packageId, String status, String decision, String decidedBy,
                                 OffsetDateTime decidedAt) {
    }
}
