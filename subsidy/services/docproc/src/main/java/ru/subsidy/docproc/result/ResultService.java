package ru.subsidy.docproc.result;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.docproc.classify.TypeRegistry;
import ru.subsidy.docproc.jooq.Tables;
import ru.subsidy.docproc.queue.JobQueue;
import ru.subsidy.docproc.queue.Stage;

/**
 * Результаты разбора: чтение по документу и пакету, смена вида специалистом, переразбор файла.
 */
@Service
public class ResultService {

    private static final String NOT_FOUND = "Документ не найден";
    private static final String LATEST = "v";

    private final DSLContext dsl;
    private final ObjectMapper json;
    private final JobQueue queue;
    private final TypeRegistry registry;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param json Jackson
     * @param queue очередь
     * @param registry реестр видов
     * @param clock часы
     */
    public ResultService(DSLContext dsl, ObjectMapper json, JobQueue queue, TypeRegistry registry, Clock clock) {
        this.dsl = dsl;
        this.json = json;
        this.queue = queue;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Последний результат документа.
     *
     * @param documentId документ
     * @return результат
     * @throws NotFoundException документа нет
     */
    @Transactional(readOnly = true)
    public DocumentResult result(UUID documentId) {
        return documents(Tables.DOCUMENTS.ID.eq(documentId)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /**
     * Документы пакета с последними результатами.
     *
     * @param packageId пакет
     * @return документы по файлам и страницам
     */
    @Transactional(readOnly = true)
    public List<DocumentResult> byPackage(UUID packageId) {
        return documents(Tables.DOCUMENTS.PACKAGE_ID.eq(packageId).and(Tables.DOCUMENTS.REMOVED.isFalse()));
    }

    /**
     * Смена вида специалистом: поля извлекаются заново по новому виду, старый результат сохраняется.
     *
     * @param documentId документ
     * @param type новый вид
     * @param reason причина
     * @param userId кто
     * @throws DomainException DOCUMENT_TYPE_INVALID
     * @throws NotFoundException документа нет
     */
    @Transactional
    public void overrideType(UUID documentId, String type, String reason, String userId) {
        if (registry.find(type).isEmpty()) {
            throw new DomainException(ErrorCode.DOCUMENT_TYPE_INVALID, "Неизвестный вид документа: " + type);
        }
        UUID fileId = dsl.update(Tables.DOCUMENTS)
                .set(Tables.DOCUMENTS.PENDING_TYPE, type)
                .set(Tables.DOCUMENTS.PENDING_REASON, reason)
                .set(Tables.DOCUMENTS.PENDING_BY, userId)
                .set(Tables.DOCUMENTS.STAGE, Stage.EXTRACTING_FIELDS)
                .set(Tables.DOCUMENTS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.DOCUMENTS.ID.eq(documentId)).and(Tables.DOCUMENTS.REMOVED.isFalse())
                .returning(Tables.DOCUMENTS.FILE_ID)
                .fetchOptional(Tables.DOCUMENTS.FILE_ID)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        queue.resume(fileId, Stage.EXTRACTING_FIELDS);
    }

    /**
     * Переразбор файла с начала.
     *
     * @param fileId файл
     * @throws NotFoundException файла нет в очереди
     */
    @Transactional
    public void reprocess(UUID fileId) {
        dsl.deleteFrom(Tables.STAGE_OUTPUTS).where(Tables.STAGE_OUTPUTS.FILE_ID.eq(fileId)).execute();
        if (!queue.restart(fileId)) {
            throw new NotFoundException("Файл не найден");
        }
    }

    private List<DocumentResult> documents(org.jooq.Condition where) {
        var latest = DSL.select(Tables.PARSE_RESULTS.DOCUMENT_ID, DSL.max(Tables.PARSE_RESULTS.VERSION).as(LATEST))
                .from(Tables.PARSE_RESULTS).groupBy(Tables.PARSE_RESULTS.DOCUMENT_ID).asTable("latest");
        return dsl.select(Tables.DOCUMENTS.asterisk(), Tables.PARSE_RESULTS.asterisk())
                .from(Tables.DOCUMENTS)
                .leftJoin(latest).on(latest.field(Tables.PARSE_RESULTS.DOCUMENT_ID).eq(Tables.DOCUMENTS.ID))
                .leftJoin(Tables.PARSE_RESULTS).on(Tables.PARSE_RESULTS.DOCUMENT_ID.eq(Tables.DOCUMENTS.ID)
                        .and(Tables.PARSE_RESULTS.VERSION.eq(latest.field(LATEST, Integer.class))))
                .where(where)
                .orderBy(Tables.DOCUMENTS.CREATED_AT, Tables.DOCUMENTS.FROM_PAGE)
                .fetch(this::toResult);
    }

    private DocumentResult toResult(Record rec) {
        JsonNode fields = json.createArrayNode();
        if (rec.get(Tables.PARSE_RESULTS.FIELDS) != null) {
            try {
                fields = json.readTree(rec.get(Tables.PARSE_RESULTS.FIELDS).data());
            } catch (JsonProcessingException broken) {
                throw new IllegalStateException("Поля не читаются", broken);
            }
        }
        return new DocumentResult(rec.get(Tables.DOCUMENTS.ID), rec.get(Tables.DOCUMENTS.FILE_ID),
                rec.get(Tables.DOCUMENTS.FILE_VERSION), rec.get(Tables.DOCUMENTS.FROM_PAGE),
                rec.get(Tables.DOCUMENTS.TO_PAGE), rec.get(Tables.DOCUMENTS.STAGE),
                rec.get(Tables.DOCUMENTS.REASON_CODE), rec.get(Tables.PARSE_RESULTS.DOC_TYPE),
                rec.get(Tables.PARSE_RESULTS.TYPE_CONFIDENCE), rec.get(Tables.PARSE_RESULTS.EVIDENCE),
                rec.get(Tables.PARSE_RESULTS.SOURCE), rec.get(Tables.PARSE_RESULTS.VERSION), fields,
                rec.get(Tables.PARSE_RESULTS.MODEL), rec.get(Tables.PARSE_RESULTS.PROMPT_VERSION),
                rec.get(Tables.PARSE_RESULTS.SCHEMA_VERSION));
    }

    /**
     * Документ и последний результат.
     *
     * @param documentId документ
     * @param fileId файл
     * @param fileVersion версия файла
     * @param fromPage первая страница
     * @param toPage последняя страница
     * @param stage стадия
     * @param reasonCode причина
     * @param type вид
     * @param typeConfidence уверенность вида
     * @param evidence признак вида
     * @param source MODEL или SPECIALIST
     * @param resultVersion версия результата
     * @param fields поля с проверками
     * @param model модель
     * @param promptVersion версия промпта
     * @param schemaVersion версия схемы
     */
    public record DocumentResult(UUID documentId, UUID fileId, int fileVersion, int fromPage, int toPage,
                                 String stage, String reasonCode, String type, BigDecimal typeConfidence,
                                 String evidence, String source, Integer resultVersion, JsonNode fields,
                                 String model, String promptVersion, String schemaVersion) {
    }
}
