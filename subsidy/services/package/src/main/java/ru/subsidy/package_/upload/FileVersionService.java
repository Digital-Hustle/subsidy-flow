package ru.subsidy.package_.upload;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.messaging.outbox.OutboxWriter;
import ru.subsidy.package_.storage.FileCrypto;
import ru.subsidy.package_.storage.FileCrypto.Sealed;
import ru.subsidy.package_.upload.UploadService.AcceptedFile;
import ru.subsidy.package_.upload.UploadService.IncomingFile;
import ru.subsidy.package_.upload.UploadService.RejectedFile;
import ru.subsidy.package_.upload.UploadService.UploadResult;

/**
 * Версии файла (FR-05/65): замена → FileReplaced → переразбор; удаление → FileRemoved.
 */
@Service
public class FileVersionService {

    private static final String DECIDED = "DECIDED";
    private static final String FILE_ID = "fileId";
    private static final String PACKAGE_ID = "packageId";

    private final DSLContext dsl;
    private final FileInspector inspector;
    private final FileCrypto crypto;
    private final OutboxWriter outbox;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param inspector проверки файла
     * @param crypto шифрование
     * @param outbox outbox
     * @param clock часы
     */
    public FileVersionService(DSLContext dsl, FileInspector inspector, FileCrypto crypto, OutboxWriter outbox,
                              Clock clock) {
        this.dsl = dsl;
        this.inspector = inspector;
        this.crypto = crypto;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Новая версия файла.
     *
     * @param packageId пакет
     * @param fileId файл
     * @param file новое содержимое
     * @param userId кто заменяет
     * @return принятая версия или причина отказа
     * @throws NotFoundException пакета или файла нет
     * @throws DomainException PACKAGE_DECIDED
     */
    @Transactional
    public UploadResult replace(UUID packageId, UUID fileId, IncomingFile file, String userId) {
        final int current = activeVersion(packageId, fileId);
        InspectionResult result = inspector.inspect(file.content());
        String sha = UploadService.sha256(file.content());
        RejectReason reason = result.reason();
        if (reason == null && FileInspector.ZIP.equals(result.mime())) {
            reason = RejectReason.ZIP_UNSAFE;
        }
        if (reason == null && isDuplicate(packageId, sha)) {
            reason = RejectReason.DUPLICATE_FILE;
        }
        if (reason != null) {
            return new UploadResult(List.of(),
                    List.of(new RejectedFile(file.name(), reason.name(), reason.detail(), reason.hint())));
        }
        int version = current + 1;
        OffsetDateTime now = OffsetDateTime.now(clock);
        Sealed sealed = crypto.encrypt(file.content(), FileCrypto.aad(fileId, version));
        dsl.insertInto(Tables.FILE_VERSIONS)
                .set(Tables.FILE_VERSIONS.FILE_ID, fileId)
                .set(Tables.FILE_VERSIONS.VERSION, version)
                .set(Tables.FILE_VERSIONS.CONTENT_ENC, sealed.content())
                .set(Tables.FILE_VERSIONS.NONCE, sealed.nonce())
                .set(Tables.FILE_VERSIONS.SHA256, sha)
                .set(Tables.FILE_VERSIONS.MIME, result.mime())
                .set(Tables.FILE_VERSIONS.SIZE_BYTES, (long) file.content().length)
                .set(Tables.FILE_VERSIONS.PAGES, result.pages())
                .set(Tables.FILE_VERSIONS.ORIGINAL_NAME, UploadService.displayName(file.name()))
                .set(Tables.FILE_VERSIONS.CREATED_BY, userId)
                .set(Tables.FILE_VERSIONS.CREATED_AT, now)
                .execute();
        dsl.update(Tables.FILES)
                .set(Tables.FILES.CURRENT_VERSION, version)
                .set(Tables.FILES.STAGE, UploadService.RECEIVED)
                .set(Tables.FILES.REASON_CODE, (String) null)
                .set(Tables.FILES.UPDATED_AT, now)
                .where(Tables.FILES.ID.eq(fileId))
                .execute();
        dsl.deleteFrom(Tables.FILE_DOCUMENTS).where(Tables.FILE_DOCUMENTS.FILE_ID.eq(fileId)).execute();
        touch(packageId, now);
        outbox.append(Topics.PACKAGE_FILE_V1, "FileReplaced", fileId.toString(), Map.of(
                FILE_ID, fileId.toString(), PACKAGE_ID, packageId.toString(), "version", version,
                "previousVersion", current, "mime", result.mime(), "pages", result.pages()));
        return new UploadResult(List.of(new AcceptedFile(fileId, UploadService.RECEIVED, null)), List.of());
    }

    /**
     * Удаляет файл из пакета (мягко: версии и аудит остаются).
     *
     * @param packageId пакет
     * @param fileId файл
     * @throws NotFoundException пакета или файла нет
     * @throws DomainException PACKAGE_DECIDED
     */
    @Transactional
    public void remove(UUID packageId, UUID fileId) {
        activeVersion(packageId, fileId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.update(Tables.FILES)
                .set(Tables.FILES.DELETED_AT, now)
                .set(Tables.FILES.UPDATED_AT, now)
                .where(Tables.FILES.ID.eq(fileId))
                .execute();
        touch(packageId, now);
        outbox.append(Topics.PACKAGE_FILE_V1, "FileRemoved", fileId.toString(), Map.of(
                FILE_ID, fileId.toString(), PACKAGE_ID, packageId.toString()));
    }

    private int activeVersion(UUID packageId, UUID fileId) {
        String status = dsl.select(Tables.PACKAGES.STATUS).from(Tables.PACKAGES)
                .where(Tables.PACKAGES.ID.eq(packageId)).forUpdate().fetchOptional(Tables.PACKAGES.STATUS)
                .orElseThrow(() -> new NotFoundException("Пакет не найден"));
        if (DECIDED.equals(status)) {
            throw new DomainException(ErrorCode.PACKAGE_DECIDED, "По пакету уже принято решение");
        }
        return dsl.select(Tables.FILES.CURRENT_VERSION).from(Tables.FILES)
                .where(Tables.FILES.ID.eq(fileId)).and(Tables.FILES.PACKAGE_ID.eq(packageId))
                .and(Tables.FILES.DELETED_AT.isNull())
                .fetchOptional(Tables.FILES.CURRENT_VERSION)
                .orElseThrow(() -> new NotFoundException("Файл не найден"));
    }

    private boolean isDuplicate(UUID packageId, String sha) {
        return dsl.fetchExists(dsl.selectOne().from(Tables.FILE_VERSIONS)
                .join(Tables.FILES).on(Tables.FILES.ID.eq(Tables.FILE_VERSIONS.FILE_ID)
                        .and(Tables.FILES.CURRENT_VERSION.eq(Tables.FILE_VERSIONS.VERSION)))
                .where(Tables.FILES.PACKAGE_ID.eq(packageId))
                .and(Tables.FILES.DELETED_AT.isNull())
                .and(Tables.FILE_VERSIONS.SHA256.eq(sha)));
    }

    private void touch(UUID packageId, OffsetDateTime now) {
        dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.UPDATED_AT, now)
                .where(Tables.PACKAGES.ID.eq(packageId)).execute();
    }
}
