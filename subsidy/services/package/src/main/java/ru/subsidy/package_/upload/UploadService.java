package ru.subsidy.package_.upload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.messaging.outbox.OutboxWriter;
import ru.subsidy.package_.storage.FileCrypto;
import ru.subsidy.package_.storage.FileCrypto.Sealed;

/**
 * Приём файлов в пакет (FR-02): проверка → AES-GCM → files + file_versions + outbox FileReceived
 * одной транзакцией на файл. Отказ одного файла не отменяет остальные.
 */
@Service
public class UploadService {

    /** Начальная стадия файла. */
    public static final String RECEIVED = "RECEIVED";
    private static final String DECIDED = "DECIDED";

    private final DSLContext dsl;
    private final TransactionTemplate transactions;
    private final FileInspector inspector;
    private final FileCrypto crypto;
    private final OutboxWriter outbox;
    private final ZipExpander zips;
    private final Clock clock;
    private final int maxFiles;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param transactions транзакции
     * @param inspector проверки
     * @param crypto шифрование
     * @param outbox outbox
     * @param zips распаковка ZIP
     * @param clock часы
     * @param maxFiles UPLOAD_MAX_FILES
     */
    public UploadService(DSLContext dsl, TransactionTemplate transactions, FileInspector inspector, FileCrypto crypto,
                         OutboxWriter outbox, ZipExpander zips, Clock clock, @Value("${UPLOAD_MAX_FILES:60}") int maxFiles) {
        this.dsl = dsl;
        this.transactions = transactions;
        this.inspector = inspector;
        this.crypto = crypto;
        this.outbox = outbox;
        this.zips = zips;
        this.clock = clock;
        this.maxFiles = maxFiles;
    }

    /**
     * Принимает файлы.
     *
     * @param packageId пакет
     * @param files файлы
     * @param userId кто загружает (из JWT)
     * @return принятые и отклонённые
     * @throws NotFoundException пакета нет
     * @throws DomainException PACKAGE_DECIDED, если по пакету уже принято решение
     */
    public UploadResult upload(UUID packageId, List<IncomingFile> files, String userId) {
        String status = dsl.select(Tables.PACKAGES.STATUS).from(Tables.PACKAGES)
                .where(Tables.PACKAGES.ID.eq(packageId)).fetchOptional(Tables.PACKAGES.STATUS)
                .orElseThrow(() -> new NotFoundException("Пакет не найден"));
        if (DECIDED.equals(status)) {
            throw new DomainException(ErrorCode.PACKAGE_DECIDED, "По пакету уже принято решение");
        }
        int present = dsl.fetchCount(Tables.FILES, Tables.FILES.PACKAGE_ID.eq(packageId)
                .and(Tables.FILES.DELETED_AT.isNull()));
        Batch batch = new Batch(present);
        for (IncomingFile file : files) {
            InspectionResult result = inspector.inspect(file.content());
            if (result.isAccepted() && FileInspector.ZIP.equals(result.mime())) {
                expand(packageId, file, userId, batch);
            } else {
                admit(packageId, file, result, userId, batch);
            }
        }
        return new UploadResult(batch.accepted(), batch.rejected());
    }

    private void expand(UUID packageId, IncomingFile archive, String userId, Batch batch) {
        List<IncomingFile> entries;
        try {
            entries = zips.expand(archive.content());
        } catch (ZipRejectedException rejection) {
            batch.reject(archive.name(), RejectReason.ZIP_UNSAFE,
                    RejectReason.ZIP_UNSAFE.detail() + ": " + rejection.getMessage());
            return;
        }
        for (IncomingFile entry : entries) {
            admit(packageId, entry, inspector.inspect(entry.content()), userId, batch);
        }
    }

    private void admit(UUID packageId, IncomingFile file, InspectionResult result, String userId, Batch batch) {
        String sha = sha256(file.content());
        RejectReason reason = reasonFor(packageId, sha, result, batch);
        if (reason == null) {
            batch.accept(transactions.execute(state -> store(packageId, file, result, sha, userId)));
        } else {
            batch.reject(file.name(), reason, reason.detail());
        }
    }

    private RejectReason reasonFor(UUID packageId, String sha, InspectionResult result, Batch batch) {
        RejectReason reason = result.reason();
        if (reason == null && FileInspector.ZIP.equals(result.mime())) {
            reason = RejectReason.ZIP_UNSAFE;
        }
        if (reason == null && isDuplicate(packageId, sha)) {
            reason = RejectReason.DUPLICATE_FILE;
        }
        if (reason == null && batch.count() >= maxFiles) {
            reason = RejectReason.TOO_MANY_FILES;
        }
        return reason;
    }

    private boolean isDuplicate(UUID packageId, String sha) {
        return dsl.fetchExists(dsl.selectOne().from(Tables.FILE_VERSIONS)
                .join(Tables.FILES).on(Tables.FILES.ID.eq(Tables.FILE_VERSIONS.FILE_ID)
                        .and(Tables.FILES.CURRENT_VERSION.eq(Tables.FILE_VERSIONS.VERSION)))
                .where(Tables.FILES.PACKAGE_ID.eq(packageId))
                .and(Tables.FILES.DELETED_AT.isNull())
                .and(Tables.FILE_VERSIONS.SHA256.eq(sha)));
    }

    private AcceptedFile store(UUID packageId, IncomingFile file, InspectionResult result, String sha,
                               String userId) {
        UUID fileId = UuidCreator.getTimeOrderedEpoch();
        int version = 1;
        OffsetDateTime now = OffsetDateTime.now(clock);
        Sealed sealed = crypto.encrypt(file.content(), FileCrypto.aad(fileId, version));
        dsl.insertInto(Tables.FILES)
                .set(Tables.FILES.ID, fileId)
                .set(Tables.FILES.PACKAGE_ID, packageId)
                .set(Tables.FILES.CURRENT_VERSION, version)
                .set(Tables.FILES.STAGE, RECEIVED)
                .set(Tables.FILES.CREATED_AT, now)
                .set(Tables.FILES.UPDATED_AT, now)
                .execute();
        dsl.insertInto(Tables.FILE_VERSIONS)
                .set(Tables.FILE_VERSIONS.FILE_ID, fileId)
                .set(Tables.FILE_VERSIONS.VERSION, version)
                .set(Tables.FILE_VERSIONS.CONTENT_ENC, sealed.content())
                .set(Tables.FILE_VERSIONS.NONCE, sealed.nonce())
                .set(Tables.FILE_VERSIONS.SHA256, sha)
                .set(Tables.FILE_VERSIONS.MIME, result.mime())
                .set(Tables.FILE_VERSIONS.SIZE_BYTES, (long) file.content().length)
                .set(Tables.FILE_VERSIONS.PAGES, result.pages())
                .set(Tables.FILE_VERSIONS.ORIGINAL_NAME, displayName(file.name()))
                .set(Tables.FILE_VERSIONS.CREATED_BY, userId)
                .set(Tables.FILE_VERSIONS.CREATED_AT, now)
                .execute();
        dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.UPDATED_AT, now)
                .where(Tables.PACKAGES.ID.eq(packageId)).execute();
        outbox.append(Topics.PACKAGE_FILE_V1, "FileReceived", fileId.toString(), Map.of(
                "fileId", fileId.toString(), "packageId", packageId.toString(), "version", version,
                "mime", result.mime(), "pages", result.pages()));
        return new AcceptedFile(fileId, RECEIVED, null);
    }

    static String displayName(String name) {
        String base = name == null ? "" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        return base.isEmpty() ? "файл" : base;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Входящий файл.
     *
     * @param name имя (только для показа)
     * @param content содержимое
     */
    public record IncomingFile(String name, byte[] content) {
    }

    /**
     * Принятый файл.
     *
     * @param fileId id
     * @param stage стадия
     * @param duplicateOf дубликат файла (PK-06)
     */
    public record AcceptedFile(UUID fileId, String stage, UUID duplicateOf) {
    }

    /**
     * Отклонённый файл.
     *
     * @param name имя
     * @param code код
     * @param detail что случилось
     * @param hint что сделать
     */
    public record RejectedFile(String name, String code, String detail, String hint) {
    }

    /**
     * Итог приёма.
     *
     * @param files принятые
     * @param rejected отклонённые
     */
    public record UploadResult(List<AcceptedFile> files, List<RejectedFile> rejected) {
    }

    /** Накопитель итога одного запроса. */
    private static final class Batch {
        private final int present;
        private final List<AcceptedFile> acceptedFiles = new ArrayList<>();
        private final List<RejectedFile> rejectedFiles = new ArrayList<>();

        Batch(int present) {
            this.present = present;
        }

        int count() {
            return present + acceptedFiles.size();
        }

        void accept(AcceptedFile file) {
            acceptedFiles.add(file);
        }

        void reject(String name, RejectReason reason, String detail) {
            rejectedFiles.add(new RejectedFile(name, reason.name(), detail, reason.hint()));
        }

        List<AcceptedFile> accepted() {
            return acceptedFiles;
        }

        List<RejectedFile> rejected() {
            return rejectedFiles;
        }
    }
}
