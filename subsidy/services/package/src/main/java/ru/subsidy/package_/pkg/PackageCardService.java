package ru.subsidy.package_.pkg;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.pkg.PackageQueryService.OrgRef;

/**
 * Карточка пакета: статус, сводка протокола, решение и файлы с найденными документами.
 */
@Service
public class PackageCardService {

    private final DSLContext dsl;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     */
    public PackageCardService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Карточка пакета.
     *
     * @param packageId пакет
     * @return карточка
     * @throws NotFoundException пакета нет
     */
    @Transactional(readOnly = true)
    public PackageCard card(UUID packageId) {
        Record row = dsl.select(Tables.PACKAGES.asterisk(), Tables.ORGANIZATIONS.INN, Tables.ORGANIZATIONS.NAME)
                .from(Tables.PACKAGES).join(Tables.ORGANIZATIONS).on(Tables.ORGANIZATIONS.ID.eq(Tables.PACKAGES.ORG_ID))
                .where(Tables.PACKAGES.ID.eq(packageId))
                .fetchOptional()
                .orElseThrow(() -> new NotFoundException("Пакет не найден"));
        ProtocolSummary protocol = null;
        if (row.get(Tables.PACKAGES.PROTOCOL_STATUS) != null) {
            protocol = new ProtocolSummary(row.get(Tables.PACKAGES.PROTOCOL_STATUS),
                    row.get(Tables.PACKAGES.COMPLETENESS), row.get(Tables.PACKAGES.FAILED),
                    row.get(Tables.PACKAGES.WARNINGS), row.get(Tables.PACKAGES.NEEDS_REVIEW));
        }
        DecisionInfo decision = null;
        if (row.get(Tables.PACKAGES.DECISION) != null) {
            decision = new DecisionInfo(row.get(Tables.PACKAGES.DECISION), row.get(Tables.PACKAGES.DECISION_COMMENT),
                    row.get(Tables.PACKAGES.DECIDED_BY), row.get(Tables.PACKAGES.DECIDED_AT));
        }
        return new PackageCard(packageId, row.get(Tables.PACKAGES.STATUS), row.get(Tables.PACKAGES.PROGRAM_CODE),
                new OrgRef(row.get(Tables.ORGANIZATIONS.INN), row.get(Tables.ORGANIZATIONS.NAME)),
                protocol, decision, row.get(Tables.PACKAGES.UPDATED_AT), files(packageId));
    }

    private List<FileCard> files(UUID packageId) {
        Map<UUID, List<DocumentCard>> documents = dsl.select(Tables.FILE_DOCUMENTS.asterisk())
                .from(Tables.FILE_DOCUMENTS).join(Tables.FILES).on(Tables.FILES.ID.eq(Tables.FILE_DOCUMENTS.FILE_ID))
                .where(Tables.FILES.PACKAGE_ID.eq(packageId)).and(Tables.FILES.DELETED_AT.isNull())
                .and(Tables.FILE_DOCUMENTS.FILE_VERSION.eq(Tables.FILES.CURRENT_VERSION))
                .orderBy(Tables.FILE_DOCUMENTS.FROM_PAGE, Tables.FILE_DOCUMENTS.DOCUMENT_ID)
                .fetchGroups(Tables.FILE_DOCUMENTS.FILE_ID, PackageCardService::toDocument);
        return dsl.select(Tables.FILES.ID, Tables.FILES.CURRENT_VERSION, Tables.FILES.STAGE, Tables.FILES.REASON_CODE,
                        Tables.FILE_VERSIONS.ORIGINAL_NAME, Tables.FILE_VERSIONS.MIME, Tables.FILE_VERSIONS.PAGES)
                .from(Tables.FILES).join(Tables.FILE_VERSIONS)
                .on(Tables.FILE_VERSIONS.FILE_ID.eq(Tables.FILES.ID))
                .and(Tables.FILE_VERSIONS.VERSION.eq(Tables.FILES.CURRENT_VERSION))
                .where(Tables.FILES.PACKAGE_ID.eq(packageId)).and(Tables.FILES.DELETED_AT.isNull())
                .orderBy(Tables.FILES.CREATED_AT, Tables.FILES.ID)
                .fetch(rec -> new FileCard(rec.get(Tables.FILES.ID), rec.get(Tables.FILES.CURRENT_VERSION),
                        rec.get(Tables.FILE_VERSIONS.ORIGINAL_NAME), rec.get(Tables.FILE_VERSIONS.MIME),
                        rec.get(Tables.FILE_VERSIONS.PAGES), rec.get(Tables.FILES.STAGE),
                        rec.get(Tables.FILES.REASON_CODE),
                        documents.getOrDefault(rec.get(Tables.FILES.ID), List.of())));
    }

    private static DocumentCard toDocument(Record rec) {
        return new DocumentCard(rec.get(Tables.FILE_DOCUMENTS.DOCUMENT_ID), rec.get(Tables.FILE_DOCUMENTS.FROM_PAGE),
                rec.get(Tables.FILE_DOCUMENTS.TO_PAGE), rec.get(Tables.FILE_DOCUMENTS.DOC_TYPE),
                rec.get(Tables.FILE_DOCUMENTS.TYPE_CONFIDENCE), rec.get(Tables.FILE_DOCUMENTS.SOURCE),
                rec.get(Tables.FILE_DOCUMENTS.STAGE), rec.get(Tables.FILE_DOCUMENTS.REASON_CODE));
    }

    /**
     * Карточка пакета.
     *
     * @param packageId пакет
     * @param status статус
     * @param program программа
     * @param org заявитель
     * @param protocol сводка последнего протокола или null
     * @param decision решение или null
     * @param updatedAt изменён
     * @param files файлы
     */
    public record PackageCard(UUID packageId, String status, String program, OrgRef org, ProtocolSummary protocol,
                              DecisionInfo decision, OffsetDateTime updatedAt, List<FileCard> files) {
    }

    /**
     * Сводка протокола (числа приходят из rules).
     *
     * @param status статус протокола
     * @param completeness комплектность, %
     * @param failed нарушений
     * @param warnings предупреждений
     * @param needsReview на ручную проверку
     */
    public record ProtocolSummary(String status, Integer completeness, int failed, int warnings, int needsReview) {
    }

    /**
     * Решение по пакету.
     *
     * @param decision решение
     * @param comment комментарий
     * @param decidedBy кто
     * @param decidedAt когда
     */
    public record DecisionInfo(String decision, String comment, String decidedBy, OffsetDateTime decidedAt) {
    }

    /**
     * Файл пакета.
     *
     * @param fileId id
     * @param version текущая версия
     * @param name исходное имя (только для показа)
     * @param mime тип содержимого
     * @param pages страниц
     * @param stage стадия
     * @param reasonCode причина
     * @param documents найденные документы
     */
    public record FileCard(UUID fileId, int version, String name, String mime, Integer pages, String stage,
                           String reasonCode, List<DocumentCard> documents) {
    }

    /**
     * Документ внутри файла.
     *
     * @param documentId id
     * @param fromPage первая страница
     * @param toPage последняя страница
     * @param type вид
     * @param typeConfidence уверенность
     * @param source кто определил вид
     * @param stage стадия
     * @param reasonCode причина
     */
    public record DocumentCard(UUID documentId, int fromPage, int toPage, String type, BigDecimal typeConfidence,
                               String source, String stage, String reasonCode) {
    }
}
