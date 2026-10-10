package ru.subsidy.package_.storage;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.storage.FileCrypto.Sealed;

/**
 * Выдача оригиналов файлов (FR-66): расшифровка и запись в file_access_audit на каждое чтение.
 */
@Service
public class FileContentService {

    /** Скачивание специалистом. */
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";
    /** Чтение сервисом разбора. */
    public static final String ACTION_INTERNAL_READ = "INTERNAL_READ";
    /** Рендер превью страницы. */
    public static final String ACTION_PREVIEW = "PREVIEW";
    private static final String NOT_FOUND = "Файл не найден";

    private final DSLContext dsl;
    private final FileCrypto crypto;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param crypto шифрование
     * @param clock часы
     */
    public FileContentService(DSLContext dsl, FileCrypto crypto, Clock clock) {
        this.dsl = dsl;
        this.crypto = crypto;
        this.clock = clock;
    }

    /**
     * Читает оригинал.
     *
     * @param fileId файл
     * @param version версия или null — текущая
     * @param actor кто читает (userId или клиент сервиса)
     * @param action действие для аудита
     * @return содержимое и метаданные
     * @throws NotFoundException файла или версии нет
     */
    @Transactional
    public FileContent read(UUID fileId, Integer version, String actor, String action) {
        int current = dsl.select(Tables.FILES.CURRENT_VERSION).from(Tables.FILES)
                .where(Tables.FILES.ID.eq(fileId)).and(Tables.FILES.DELETED_AT.isNull())
                .fetchOptional(Tables.FILES.CURRENT_VERSION)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        int wanted = version == null ? current : version;
        var row = dsl.selectFrom(Tables.FILE_VERSIONS)
                .where(Tables.FILE_VERSIONS.FILE_ID.eq(fileId)).and(Tables.FILE_VERSIONS.VERSION.eq(wanted))
                .fetchOptional()
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        byte[] plain = crypto.decrypt(new Sealed(row.getContentEnc(), row.getNonce()), FileCrypto.aad(fileId, wanted));
        dsl.insertInto(Tables.FILE_ACCESS_AUDIT)
                .set(Tables.FILE_ACCESS_AUDIT.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.FILE_ACCESS_AUDIT.FILE_ID, fileId)
                .set(Tables.FILE_ACCESS_AUDIT.VERSION, wanted)
                .set(Tables.FILE_ACCESS_AUDIT.USER_ID, actor)
                .set(Tables.FILE_ACCESS_AUDIT.ACTION, action)
                .set(Tables.FILE_ACCESS_AUDIT.AT, OffsetDateTime.now(clock))
                .execute();
        return new FileContent(plain, row.getMime(), row.getOriginalName(), wanted);
    }

    /**
     * Оригинал файла.
     *
     * @param content байты
     * @param mime MIME
     * @param name имя для скачивания
     * @param version версия
     */
    public record FileContent(byte[] content, String mime, String name, int version) {
    }
}
