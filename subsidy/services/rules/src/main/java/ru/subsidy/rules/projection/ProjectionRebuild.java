package ru.subsidy.rules.projection;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.rules.jooq.Tables;

/**
 * Пересборка проекции документов пакета из docproc (RUL-18), когда событие DocumentParsed потеряно или
 * пришло не по порядку. Вручную — SENIOR_SPECIALIST; автоматически — перед расчётом протокола, если у
 * разобранного файла в проекции нет ни одного документа.
 */
@Service
public class ProjectionRebuild {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectionRebuild.class);
    private static final List<String> PARSED_STAGES = List.of("PARSED", "NEEDS_REVIEW");

    private final DSLContext dsl;
    private final DocprocClient docproc;
    private final ProjectionService projection;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param docproc клиент docproc
     * @param projection проекция
     */
    public ProjectionRebuild(DSLContext dsl, DocprocClient docproc, ProjectionService projection) {
        this.dsl = dsl;
        this.docproc = docproc;
        this.projection = projection;
    }

    /**
     * Пересобирает документы пакета по docproc и ставит перепроверку.
     *
     * @param packageId пакет
     * @return сколько документов получено, обновлено и снято
     * @throws DomainException PACKAGE_NOT_FOUND или DEPENDENCY_UNAVAILABLE
     */
    public ProjectionService.Reconciled rebuild(UUID packageId) {
        boolean known = dsl.fetchExists(Tables.PACKAGE_VIEW, Tables.PACKAGE_VIEW.PACKAGE_ID.eq(packageId));
        if (!known) {
            throw new DomainException(ErrorCode.PACKAGE_NOT_FOUND, "Пакет не найден");
        }
        List<JsonNode> documents = docproc.documents(packageId);
        return projection.reconcile(packageId, documents);
    }

    /**
     * Пересборка перед расчётом протокола, если проекция явно неполна. Ошибка docproc не мешает расчёту.
     *
     * @param packageId пакет
     * @return true, если пересборка выполнена
     */
    public boolean rebuildIfIncomplete(UUID packageId) {
        if (!incomplete(packageId)) {
            return false;
        }
        try {
            ProjectionService.Reconciled done = rebuild(packageId);
            LOG.info("projection rebuilt package={} documents={} updated={} removed={}", packageId,
                    done.documents(), done.updated(), done.removed());
            return true;
        } catch (DomainException failure) {
            LOG.warn("projection rebuild skipped package={}: {}", packageId, failure.code());
            return false;
        }
    }

    /**
     * Есть ли разобранный файл (PARSED/NEEDS_REVIEW) без документов в проекции.
     *
     * @param packageId пакет
     * @return true, если проекция неполна
     */
    public boolean incomplete(UUID packageId) {
        var docs = Tables.PACKAGE_DOCUMENTS;
        var files = Tables.PACKAGE_FILES;
        return dsl.fetchExists(DSL.selectOne().from(files)
                .where(files.PACKAGE_ID.eq(packageId)).and(files.REMOVED.isFalse())
                .and(files.STAGE.in(PARSED_STAGES))
                .andNotExists(DSL.selectOne().from(docs).where(docs.FILE_ID.eq(files.FILE_ID))
                        .and(docs.FILE_VERSION.eq(files.VERSION)).and(docs.REMOVED.isFalse())));
    }
}
