package ru.subsidy.package_.applicant;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.package_.applicant.ApplicantAccess.Access;
import ru.subsidy.package_.applicant.ApplicantPrograms.Required;
import ru.subsidy.package_.applicant.ApplicantViews.ApplicantApplication;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.pkg.ApplicationData;
import ru.subsidy.package_.pkg.ApplicationService;
import ru.subsidy.package_.upload.FileVersionService;
import ru.subsidy.package_.upload.UploadService;
import ru.subsidy.package_.upload.UploadService.IncomingFile;
import ru.subsidy.package_.upload.UploadService.UploadResult;

/**
 * Сценарии кабинета заявителя поверх пакета: анкета, документы, отправка, удаление, поток событий.
 */
@Service
public class ApplicantService {

    private static final String APPLICATION_FIELD = "application";

    private final DSLContext dsl;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final ApplicantAccess access;
    private final ApplicantViews views;
    private final ApplicationService applications;
    private final UploadService uploads;
    private final FileVersionService versions;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     * @param transactions транзакции
     * @param access доступ по ключу
     * @param views представления
     * @param applications анкеты
     * @param uploads приём файлов
     * @param versions версии файлов
     */
    public ApplicantService(DSLContext dsl, Clock clock, TransactionTemplate transactions, ApplicantAccess access,
                            ApplicantViews views, ApplicationService applications, UploadService uploads,
                            FileVersionService versions) {
        this.dsl = dsl;
        this.clock = clock;
        this.transactions = transactions;
        this.access = access;
        this.views = views;
        this.applications = applications;
        this.uploads = uploads;
        this.versions = versions;
    }

    /**
     * Новая заявка.
     *
     * @param consent согласие на обработку персональных данных
     * @param programCode программа
     * @param email почта
     * @param phone телефон или null
     * @param idempotencyKey ключ идемпотентности
     * @return доступ
     * @throws DomainException CONSENT_REQUIRED, PROGRAM_UNKNOWN
     */
    public Access create(boolean consent, String programCode, String email, String phone, String idempotencyKey) {
        if (!consent) {
            throw new DomainException(ErrorCode.CONSENT_REQUIRED, "Нужно согласие на обработку персональных данных");
        }
        if (!views.isOpen(programCode)) {
            throw new DomainException(ErrorCode.PROGRAM_UNKNOWN, "Программа не найдена или приём заявок закрыт");
        }
        return access.create(programCode, email, phone == null ? null : ApplicantAccess.phone(phone), idempotencyKey);
    }

    /**
     * Заявка.
     *
     * @param id заявка
     * @param key заголовок Authorization
     * @return заявка
     */
    public ApplicantApplication get(UUID id, String key) {
        access.authorize(id, key);
        return views.view(views.row(id));
    }

    /**
     * Сохраняет анкету; первая анкета создаёт организацию и публикует PackageCreated.
     *
     * @param id заявка
     * @param key ключ
     * @param expectedRevision ожидаемая ревизия
     * @param application анкета
     * @return новая ревизия и чек-лист
     */
    public Saved saveApplication(UUID id, String key, int expectedRevision, ApplicationData application) {
        Record row = editable(id, key);
        int revision = transactions.execute(state -> {
            if (row.get(Tables.PACKAGES.ORG_ID) == null) {
                access.attachOrganization(id, application.orgInn(), row.get(Tables.PACKAGES.PROGRAM_CODE));
            }
            return applications.update(id, expectedRevision, application, ApplicantAccess.APPLICANT)
                    .applicationRevision();
        });
        return new Saved(revision, views.required(row.get(Tables.PACKAGES.PROGRAM_CODE), application));
    }

    /**
     * Загружает документ в пункт чек-листа.
     *
     * @param id заявка
     * @param key ключ
     * @param file файл
     * @param slotType пункт (подсказка)
     * @return принятый документ
     * @throws UploadRejectedException файл не принят
     */
    public Uploaded upload(UUID id, String key, IncomingFile file, String slotType) {
        editable(id, key);
        return accept(id, uploads.upload(id, List.of(file), ApplicantAccess.APPLICANT), slotType);
    }

    /**
     * Добавляет файл к документу или заменяет его.
     *
     * @param id заявка
     * @param key ключ
     * @param documentId документ (= fileId)
     * @param file файл
     * @param replace true — заменить, false — добавить файлом в тот же пункт
     * @return принятый документ
     */
    public Uploaded addFile(UUID id, String key, UUID documentId, IncomingFile file, boolean replace) {
        editable(id, key);
        String slot = dsl.select(Tables.FILES.ID, Tables.FILES.SLOT_TYPE).from(Tables.FILES)
                .where(Tables.FILES.ID.eq(documentId)).and(Tables.FILES.PACKAGE_ID.eq(id))
                .and(Tables.FILES.DELETED_AT.isNull()).fetchOptional()
                .orElseThrow(() -> new DomainException(ErrorCode.DOCUMENT_NOT_FOUND, "Документ не найден"))
                .get(Tables.FILES.SLOT_TYPE);
        UploadResult result = replace ? versions.replace(id, documentId, file, ApplicantAccess.APPLICANT)
                : uploads.upload(id, List.of(file), ApplicantAccess.APPLICANT);
        return accept(id, result, slot);
    }

    /**
     * Удаляет документ.
     *
     * @param id заявка
     * @param key ключ
     * @param documentId документ
     */
    public void removeDocument(UUID id, String key, UUID documentId) {
        editable(id, key);
        versions.remove(id, documentId);
    }

    /**
     * Удаляет черновик и отзывает согласие: файлы, анкета и контакты стираются.
     *
     * @param id заявка
     * @param key ключ
     * @throws DomainException PACKAGE_LOCKED — заявка уже отправлена
     */
    public void delete(UUID id, String key) {
        access.authorize(id, key);
        Record row = views.row(id);
        if (!ApplicantViews.DRAFT.equals(ApplicantViews.status(row))) {
            throw new DomainException(ErrorCode.PACKAGE_LOCKED,
                    "Отправленную заявку удалить нельзя — обратитесь в агентство");
        }
        transactions.executeWithoutResult(state -> {
            for (var document : views.documents(id)) {
                versions.remove(id, document.fileId());
            }
            var fileIds = dsl.select(Tables.FILES.ID).from(Tables.FILES).where(Tables.FILES.PACKAGE_ID.eq(id));
            dsl.deleteFrom(Tables.PAGE_PREVIEWS).where(Tables.PAGE_PREVIEWS.FILE_ID.in(fileIds)).execute();
            dsl.update(Tables.FILE_VERSIONS).set(Tables.FILE_VERSIONS.CONTENT_ENC, new byte[0])
                    .where(Tables.FILE_VERSIONS.FILE_ID.in(fileIds)).execute();
            dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.APPLICATION, (JSONB) null)
                    .set(Tables.PACKAGES.UPDATED_AT, OffsetDateTime.now(clock))
                    .where(Tables.PACKAGES.ID.eq(id)).execute();
            access.revoke(id);
        });
    }

    /**
     * Отправляет заявку специалисту.
     *
     * @param id заявка
     * @param key ключ
     * @return статус
     * @throws DocumentsMissingException не хватает документов или анкеты
     */
    public Submitted submit(UUID id, String key) {
        Record row = editable(id, key);
        ApplicationData application = applications.read(row.get(Tables.PACKAGES.APPLICATION));
        List<Required> required = views.required(row.get(Tables.PACKAGES.PROGRAM_CODE), application);
        List<String> missing = ApplicantViews.missing(required, views.documents(id));
        List<String> missingFields = new ArrayList<>();
        if (application == null) {
            missingFields.add(APPLICATION_FIELD);
        }
        if (!missing.isEmpty() || !missingFields.isEmpty()) {
            throw new DocumentsMissingException(missing, missingFields);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        transactions.executeWithoutResult(state -> {
            dsl.update(Tables.APPLICANTS).set(Tables.APPLICANTS.STATE, ApplicantViews.SUBMITTED)
                    .set(Tables.APPLICANTS.SUBMITTED_AT, now)
                    .where(Tables.APPLICANTS.PACKAGE_ID.eq(id)).execute();
            dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.UPDATED_AT, now)
                    .where(Tables.PACKAGES.ID.eq(id)).execute();
        });
        return new Submitted(ApplicantViews.SUBMITTED, List.of());
    }

    private Record editable(UUID id, String key) {
        access.authorize(id, key);
        Record row = views.row(id);
        String status = ApplicantViews.status(row);
        if (ApplicantViews.NEEDS_FIX.equals(status) && !status.equals(row.get(Tables.APPLICANTS.STATE))) {
            dsl.update(Tables.APPLICANTS).set(Tables.APPLICANTS.STATE, ApplicantViews.NEEDS_FIX)
                    .where(Tables.APPLICANTS.PACKAGE_ID.eq(id)).execute();
        }
        if (!ApplicantViews.DRAFT.equals(status) && !ApplicantViews.NEEDS_FIX.equals(status)) {
            throw new DomainException(ErrorCode.PACKAGE_LOCKED,
                    "Заявка у специалиста — изменения станут доступны, если он вернёт её на доработку");
        }
        return row;
    }

    private Uploaded accept(UUID id, UploadResult result, String slotType) {
        if (result.files().isEmpty()) {
            var rejected = result.rejected().isEmpty() ? null : result.rejected().getFirst();
            throw rejected == null ? new UploadRejectedException("FILE_EMPTY", "Файл не принят", null)
                    : new UploadRejectedException(rejected.code(), rejected.detail(), rejected.hint());
        }
        var first = result.files().getFirst();
        if (slotType != null) {
            dsl.update(Tables.FILES).set(Tables.FILES.SLOT_TYPE, slotType)
                    .where(Tables.FILES.ID.in(result.files().stream().map(UploadService.AcceptedFile::fileId).toList()))
                    .and(Tables.FILES.PACKAGE_ID.eq(id)).execute();
        }
        List<String> warnings = result.rejected().stream().map(rejected -> rejected.name() + ": " + rejected.detail()).toList();
        return new Uploaded(first.fileId(), first.fileId(), first.stage(), first.duplicateOf(), warnings);
    }

    /**
     * Анкета сохранена.
     *
     * @param applicationRevision ревизия
     * @param requiredDocuments чек-лист
     */
    public record Saved(int applicationRevision, List<Required> requiredDocuments) {
    }

    /**
     * Документ принят.
     *
     * @param documentId документ
     * @param fileId файл
     * @param stage стадия
     * @param duplicateOf дубликат
     * @param warnings предупреждения (отклонённые файлы из архива)
     */
    public record Uploaded(UUID documentId, UUID fileId, String stage, UUID duplicateOf, List<String> warnings) {
    }

    /**
     * Заявка отправлена.
     *
     * @param status статус
     * @param missing недостающие (пусто)
     */
    public record Submitted(String status, List<String> missing) {
    }
}
