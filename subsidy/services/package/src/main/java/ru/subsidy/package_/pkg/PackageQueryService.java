package ru.subsidy.package_.pkg;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.package_.jooq.Tables;

/**
 * Очередь пакетов (FR-61): фильтры, keyset-курсор по (updated_at, id), счётчики из протокола.
 */
@Service
public class PackageQueryService {

    /** Размер страницы по умолчанию. */
    public static final int DEFAULT_LIMIT = 50;
    /** Максимальный размер страницы. */
    public static final int MAX_LIMIT = 100;
    private static final String SEPARATOR = "|";

    private final DSLContext dsl;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     */
    public PackageQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Страница очереди, свежие сверху.
     *
     * @param filter фильтры (любое поле может быть null)
     * @param cursor курсор следующей страницы или null
     * @param limit размер страницы или null
     * @return страница
     * @throws DomainException VALIDATION_ERROR — битый курсор
     */
    @Transactional(readOnly = true)
    public QueuePage list(QueueFilter filter, String cursor, Integer limit) {
        final int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        Condition where = DSL.noCondition();
        if (filter.status() != null) {
            where = where.and(Tables.PACKAGES.STATUS.eq(filter.status()));
        }
        if (filter.inn() != null) {
            where = where.and(Tables.ORGANIZATIONS.INN.eq(filter.inn()));
        }
        if (filter.program() != null) {
            where = where.and(Tables.PACKAGES.PROGRAM_CODE.eq(filter.program()));
        }
        int total = dsl.fetchCount(dsl.selectOne().from(Tables.PACKAGES).join(Tables.ORGANIZATIONS)
                .on(Tables.ORGANIZATIONS.ID.eq(Tables.PACKAGES.ORG_ID)).where(where));
        Condition page = where;
        if (cursor != null && !cursor.isBlank()) {
            Key key = decode(cursor);
            page = page.and(DSL.row(Tables.PACKAGES.UPDATED_AT, Tables.PACKAGES.ID).lt(key.updatedAt(), key.id()));
        }
        List<QueueItem> rows = dsl.select(Tables.PACKAGES.ID, Tables.PACKAGES.STATUS, Tables.PACKAGES.PROGRAM_CODE,
                        Tables.PACKAGES.PROTOCOL_STATUS, Tables.PACKAGES.COMPLETENESS, Tables.PACKAGES.FAILED,
                        Tables.PACKAGES.WARNINGS, Tables.PACKAGES.NEEDS_REVIEW, Tables.PACKAGES.UPDATED_AT,
                        Tables.ORGANIZATIONS.INN, Tables.ORGANIZATIONS.NAME)
                .from(Tables.PACKAGES).join(Tables.ORGANIZATIONS).on(Tables.ORGANIZATIONS.ID.eq(Tables.PACKAGES.ORG_ID))
                .where(page)
                .orderBy(Tables.PACKAGES.UPDATED_AT.desc(), Tables.PACKAGES.ID.desc())
                .limit(size + 1)
                .fetch(PackageQueryService::toItem);
        String next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            QueueItem last = rows.get(size - 1);
            next = encode(last.updatedAt(), last.id());
        }
        return new QueuePage(List.copyOf(rows), new PageInfo(next, total));
    }

    private static QueueItem toItem(Record rec) {
        return new QueueItem(rec.get(Tables.PACKAGES.ID),
                new OrgRef(rec.get(Tables.ORGANIZATIONS.INN), rec.get(Tables.ORGANIZATIONS.NAME)),
                rec.get(Tables.PACKAGES.PROGRAM_CODE), rec.get(Tables.PACKAGES.STATUS),
                rec.get(Tables.PACKAGES.PROTOCOL_STATUS), rec.get(Tables.PACKAGES.COMPLETENESS),
                rec.get(Tables.PACKAGES.FAILED), rec.get(Tables.PACKAGES.WARNINGS),
                rec.get(Tables.PACKAGES.NEEDS_REVIEW), rec.get(Tables.PACKAGES.UPDATED_AT));
    }

    static String encode(OffsetDateTime updatedAt, UUID id) {
        String raw = updatedAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Key decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.indexOf(SEPARATOR);
            return new Key(OffsetDateTime.parse(raw.substring(0, split)), UUID.fromString(raw.substring(split + 1)));
        } catch (IllegalArgumentException | DateTimeParseException | StringIndexOutOfBoundsException broken) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Неверный курсор страницы");
        }
    }

    record Key(OffsetDateTime updatedAt, UUID id) {
    }

    /**
     * Фильтры очереди.
     *
     * @param status статус пакета
     * @param inn ИНН заявителя
     * @param program код программы
     */
    public record QueueFilter(String status, String inn, String program) {
    }

    /**
     * Строка очереди.
     *
     * @param id пакет
     * @param org заявитель
     * @param program программа
     * @param status статус пакета
     * @param protocolStatus статус последнего протокола
     * @param completeness комплектность, %
     * @param failed нарушений
     * @param warnings предупреждений
     * @param needsReview пунктов на ручную проверку
     * @param updatedAt последнее изменение
     */
    public record QueueItem(UUID id, OrgRef org, String program, String status, String protocolStatus,
                            Integer completeness, int failed, int warnings, int needsReview,
                            OffsetDateTime updatedAt) {
    }

    /**
     * Заявитель в строке очереди.
     *
     * @param inn ИНН
     * @param name наименование
     */
    public record OrgRef(String inn, String name) {
    }

    /**
     * Страница очереди.
     *
     * @param items строки
     * @param page курсор и общее число
     */
    public record QueuePage(List<QueueItem> items, PageInfo page) {
    }

    /**
     * Пагинация.
     *
     * @param next курсор следующей страницы или null
     * @param total всего по фильтрам
     */
    public record PageInfo(String next, int total) {
    }
}
