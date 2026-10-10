package ru.subsidy.package_.applicant;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import ru.subsidy.package_.applicant.ApplicantPrograms.Required;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.pkg.ApplicationData;
import ru.subsidy.package_.pkg.ApplicationService;

/**
 * Заявка глазами заявителя: анкета, чек-лист, документы и решение — без извлечённых полей и проверок.
 */
@Component
public class ApplicantViews {

    /** Черновик. */
    public static final String DRAFT = "DRAFT";
    /** Отправлена специалисту. */
    public static final String SUBMITTED = "SUBMITTED";
    /** Возвращена на доработку. */
    public static final String NEEDS_FIX = "NEEDS_FIX";
    /** Решение принято. */
    public static final String DECIDED = "DECIDED";
    private static final String RETURN = "RETURN";
    private static final String SIGNATURE_PRESENT = "PRESENT";

    private final DSLContext dsl;
    private final ApplicationService applications;
    private final ApplicantPrograms programs;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param applications анкеты
     * @param programs программы
     */
    public ApplicantViews(DSLContext dsl, ApplicationService applications, ApplicantPrograms programs) {
        this.dsl = dsl;
        this.applications = applications;
        this.programs = programs;
    }

    /**
     * Статус заявки для заявителя.
     *
     * @param row пакет с заявителем
     * @return DRAFT, SUBMITTED, NEEDS_FIX или DECIDED
     */
    public static String status(Record row) {
        if (DECIDED.equals(row.get(Tables.PACKAGES.STATUS))) {
            return DECIDED;
        }
        String state = row.get(Tables.APPLICANTS.STATE);
        OffsetDateTime submitted = row.get(Tables.APPLICANTS.SUBMITTED_AT);
        OffsetDateTime decided = row.get(Tables.PACKAGES.DECIDED_AT);
        boolean returned = RETURN.equals(row.get(Tables.PACKAGES.DECISION)) && decided != null
                && (submitted == null || decided.isAfter(submitted));
        return SUBMITTED.equals(state) && returned ? NEEDS_FIX : state;
    }

    /**
     * Открыта ли программа для новых заявок.
     *
     * @param programCode программа
     * @return true, если идёт приём
     */
    public boolean isOpen(String programCode) {
        return programs.isOpen(programCode);
    }

    /**
     * Строка пакета с данными заявителя.
     *
     * @param applicationId заявка
     * @return строка
     */
    public Record row(UUID applicationId) {
        return dsl.select(Tables.PACKAGES.asterisk(), Tables.APPLICANTS.asterisk()).from(Tables.PACKAGES)
                .join(Tables.APPLICANTS).on(Tables.APPLICANTS.PACKAGE_ID.eq(Tables.PACKAGES.ID))
                .where(Tables.PACKAGES.ID.eq(applicationId)).fetchOne();
    }

    /**
     * Чек-лист для анкеты.
     *
     * @param programCode программа
     * @param application анкета или null
     * @return пункты
     */
    public List<Required> required(String programCode, ApplicationData application) {
        Map<String, Boolean> conditions = application == null ? null : application.conditions();
        return programs.find(programCode).map(program -> ApplicantPrograms.checklist(program, conditions))
                .orElse(List.of());
    }

    /**
     * Недостающие обязательные пункты (по основному виду пункта).
     *
     * @param required чек-лист
     * @param documents документы заявки
     * @return виды
     */
    public static List<String> missing(List<Required> required, List<ApplicantDocument> documents) {
        Set<String> present = new HashSet<>();
        for (ApplicantDocument document : documents) {
            present.add(document.effectiveType());
        }
        List<String> missing = new ArrayList<>();
        for (Required item : required) {
            if (item.required() && item.types().stream().noneMatch(present::contains)) {
                missing.add(item.type());
            }
        }
        return missing;
    }

    /**
     * Документы (файлы) заявки.
     *
     * @param applicationId заявка
     * @return документы
     */
    public List<ApplicantDocument> documents(UUID applicationId) {
        var detected = dsl.select(Tables.FILE_DOCUMENTS.DOC_TYPE).from(Tables.FILE_DOCUMENTS)
                .where(Tables.FILE_DOCUMENTS.FILE_ID.eq(Tables.FILES.ID))
                .and(Tables.FILE_DOCUMENTS.FILE_VERSION.eq(Tables.FILES.CURRENT_VERSION))
                .orderBy(Tables.FILE_DOCUMENTS.FROM_PAGE).limit(1).asField("detected_type");
        return dsl.select(Tables.FILES.ID, Tables.FILES.SLOT_TYPE, Tables.FILES.STAGE, Tables.FILES.REASON_CODE,
                        Tables.FILES.UPDATED_AT, Tables.FILE_VERSIONS.ORIGINAL_NAME, Tables.FILE_VERSIONS.PAGES,
                        Tables.FILE_VERSIONS.SIGNATURE_STATUS, detected)
                .from(Tables.FILES).join(Tables.FILE_VERSIONS).on(Tables.FILE_VERSIONS.FILE_ID.eq(Tables.FILES.ID)
                        .and(Tables.FILE_VERSIONS.VERSION.eq(Tables.FILES.CURRENT_VERSION)))
                .where(Tables.FILES.PACKAGE_ID.eq(applicationId)).and(Tables.FILES.DELETED_AT.isNull())
                .orderBy(Tables.FILES.CREATED_AT)
                .fetch(rec -> new ApplicantDocument(rec.get(Tables.FILES.ID), rec.get(Tables.FILES.ID),
                        rec.get(Tables.FILES.SLOT_TYPE), (String) rec.get(detected),
                        rec.get(Tables.FILE_VERSIONS.ORIGINAL_NAME), rec.get(Tables.FILE_VERSIONS.PAGES),
                        rec.get(Tables.FILES.STAGE), rec.get(Tables.FILES.REASON_CODE),
                        SIGNATURE_PRESENT.equals(rec.get(Tables.FILE_VERSIONS.SIGNATURE_STATUS)) ? Boolean.TRUE : null,
                        rec.get(Tables.FILES.UPDATED_AT)));
    }

    /**
     * Полная заявка.
     *
     * @param row строка из {@link #row(UUID)}
     * @return заявка
     */
    public ApplicantApplication view(Record row) {
        UUID id = row.get(Tables.PACKAGES.ID);
        String programCode = row.get(Tables.PACKAGES.PROGRAM_CODE);
        ApplicationData application = applications.read(row.get(Tables.PACKAGES.APPLICATION));
        JsonNode program = programs.find(programCode).orElse(null);
        List<Required> required = program == null ? List.of()
                : ApplicantPrograms.checklist(program, application == null ? null : application.conditions());
        List<ApplicantDocument> documents = documents(id);
        Decision decision = row.get(Tables.PACKAGES.DECISION) == null ? null
                : new Decision(row.get(Tables.PACKAGES.DECISION), row.get(Tables.PACKAGES.DECISION_COMMENT),
                row.get(Tables.PACKAGES.DECIDED_AT));
        return new ApplicantApplication(id, status(row), programCode,
                program == null ? programCode : ApplicantPrograms.name(program), application,
                row.get(Tables.PACKAGES.APPLICATION_REVISION), required, documents, missing(required, documents),
                List.of(), decision, row.get(Tables.APPLICANTS.SUBMITTED_AT), row.get(Tables.PACKAGES.UPDATED_AT),
                row.get(Tables.PACKAGES.CREATED_AT));
    }

    /**
     * Документ заявителя (один загруженный файл).
     *
     * @param id id документа (= fileId)
     * @param fileId файл
     * @param slotType пункт, в который загрузили
     * @param detectedType вид, определённый по содержимому
     * @param fileName имя файла
     * @param pages страниц
     * @param stage стадия обработки
     * @param reasonCode причина отказа/проблемы
     * @param electronicSignature true, если в PDF найдена электронная подпись
     * @param updatedAt изменён
     */
    public record ApplicantDocument(UUID id, UUID fileId, String slotType, String detectedType, String fileName,
                                    Integer pages, String stage, String reasonCode, Boolean electronicSignature,
                                    OffsetDateTime updatedAt) {

        /**
         * Вид для чек-листа: определённый по содержимому, иначе пункт загрузки.
         *
         * @return вид
         */
        public String effectiveType() {
            return detectedType != null && !"UNKNOWN".equals(detectedType) ? detectedType : slotType;
        }
    }

    /**
     * Решение по заявке.
     *
     * @param decision APPROVE, REJECT или RETURN
     * @param comment комментарий
     * @param decidedAt когда
     */
    public record Decision(String decision, String comment, OffsetDateTime decidedAt) {
    }

    /**
     * Заявка для кабинета.
     *
     * @param applicationId id
     * @param status статус для заявителя
     * @param programCode программа
     * @param programName название программы
     * @param application анкета
     * @param applicationRevision ревизия анкеты
     * @param requiredDocuments чек-лист
     * @param documents документы
     * @param missing недостающие обязательные виды
     * @param operatorRequests запросы специалиста
     * @param decision решение
     * @param submittedAt отправлена
     * @param updatedAt изменена
     * @param createdAt создана
     */
    public record ApplicantApplication(UUID applicationId, String status, String programCode, String programName,
                                       ApplicationData application, int applicationRevision,
                                       List<Required> requiredDocuments, List<ApplicantDocument> documents,
                                       List<String> missing, List<Object> operatorRequests, Decision decision,
                                       OffsetDateTime submittedAt, OffsetDateTime updatedAt,
                                       OffsetDateTime createdAt) {
    }
}
