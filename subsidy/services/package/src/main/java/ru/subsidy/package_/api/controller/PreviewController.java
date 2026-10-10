package ru.subsidy.package_.api.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.preview.PreviewService;

/**
 * Превью страниц для карточки пакета (FR-63).
 */
@RestController
public class PreviewController {

    private final PreviewService previews;

    /**
     * Конструктор.
     *
     * @param previews превью
     */
    public PreviewController(PreviewService previews) {
        this.previews = previews;
    }

    /**
     * PNG страницы.
     *
     * @param fileId файл
     * @param page страница с 1
     * @param version версия (по умолчанию текущая)
     * @param user текущий пользователь
     * @param response ответ (заголовки)
     * @return PNG
     */
    @GetMapping(value = "/api/v1/files/{fileId}/pages/{page}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')")
    public byte[] page(@PathVariable UUID fileId, @PathVariable int page,
                       @RequestParam(required = false) Integer version, CurrentUser user,
                       HttpServletResponse response) {
        byte[] png = previews.page(fileId, version, page, user.userId());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
        response.setHeader("X-Content-Type-Options", "nosniff");
        return png;
    }
}
