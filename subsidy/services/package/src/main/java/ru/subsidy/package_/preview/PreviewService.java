package ru.subsidy.package_.preview;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.storage.FileContentService;
import ru.subsidy.package_.storage.FileContentService.FileContent;

/**
 * Превью страниц (FR-63): кэш page_previews по (файл, версия, страница), промах → расшифровка + рендер.
 */
@Service
public class PreviewService {

    private final DSLContext dsl;
    private final FileContentService contents;
    private final PageRenderer renderer;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param contents чтение файлов с аудитом
     * @param renderer рендер страниц
     * @param clock часы
     */
    public PreviewService(DSLContext dsl, FileContentService contents, PageRenderer renderer, Clock clock) {
        this.dsl = dsl;
        this.contents = contents;
        this.renderer = renderer;
        this.clock = clock;
    }

    /**
     * PNG страницы.
     *
     * @param fileId файл
     * @param version версия или null — текущая
     * @param page страница с 1
     * @param actor кто смотрит
     * @return PNG
     * @throws NotFoundException файла, версии или страницы нет
     */
    @Transactional
    public byte[] page(UUID fileId, Integer version, int page, String actor) {
        int wanted = version == null ? currentVersion(fileId) : version;
        byte[] cached = dsl.select(Tables.PAGE_PREVIEWS.PNG).from(Tables.PAGE_PREVIEWS)
                .where(Tables.PAGE_PREVIEWS.FILE_ID.eq(fileId))
                .and(Tables.PAGE_PREVIEWS.VERSION.eq(wanted))
                .and(Tables.PAGE_PREVIEWS.PAGE.eq(page))
                .fetchOptional(Tables.PAGE_PREVIEWS.PNG).orElse(null);
        if (cached != null) {
            currentVersion(fileId);
            return cached;
        }
        FileContent file = contents.read(fileId, wanted, actor, FileContentService.ACTION_PREVIEW);
        byte[] png = renderer.render(file.content(), file.mime(), page);
        dsl.insertInto(Tables.PAGE_PREVIEWS)
                .set(Tables.PAGE_PREVIEWS.FILE_ID, fileId)
                .set(Tables.PAGE_PREVIEWS.VERSION, wanted)
                .set(Tables.PAGE_PREVIEWS.PAGE, page)
                .set(Tables.PAGE_PREVIEWS.PNG, png)
                .set(Tables.PAGE_PREVIEWS.CREATED_AT, OffsetDateTime.now(clock))
                .onConflictDoNothing()
                .execute();
        return png;
    }

    private int currentVersion(UUID fileId) {
        return dsl.select(Tables.FILES.CURRENT_VERSION).from(Tables.FILES)
                .where(Tables.FILES.ID.eq(fileId)).and(Tables.FILES.DELETED_AT.isNull())
                .fetchOptional(Tables.FILES.CURRENT_VERSION)
                .orElseThrow(() -> new NotFoundException("Файл не найден"));
    }
}
