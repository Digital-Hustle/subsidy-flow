package ru.subsidy.package_.api.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.storage.FileContentService;
import ru.subsidy.package_.storage.FileContentService.FileContent;

/**
 * Оригинал файла для специалиста (FR-66); аудитору оригиналы не выдаются.
 */
@RestController
public class FileContentController {

    private final FileContentService contents;

    /**
     * Конструктор.
     *
     * @param contents выдача файлов
     */
    public FileContentController(FileContentService contents) {
        this.contents = contents;
    }

    /**
     * Скачивание оригинала.
     *
     * @param fileId файл
     * @param version версия (по умолчанию текущая)
     * @param user текущий пользователь
     * @param response HTTP-ответ для заголовков
     * @return содержимое файла
     */
    @GetMapping("/api/v1/files/{fileId}/content")
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public byte[] content(@PathVariable UUID fileId, @RequestParam(required = false) Integer version,
                          CurrentUser user, HttpServletResponse response) {
        FileContent file = contents.read(fileId, version, user.userId(), FileContentService.ACTION_DOWNLOAD);
        response.setContentType(file.mime());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return file.content();
    }
}
