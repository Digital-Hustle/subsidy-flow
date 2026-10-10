package ru.subsidy.package_.api.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

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
 * Внутренняя выдача файла для svc-docproc; gateway наружу внутренние пути не пропускает.
 */
@RestController
public class InternalFilesController {

    private final FileContentService contents;

    /**
     * Конструктор.
     *
     * @param contents выдача файлов
     */
    public InternalFilesController(FileContentService contents) {
        this.contents = contents;
    }

    /**
     * Файл для разбора.
     *
     * @param fileId файл
     * @param version версия (по умолчанию текущая)
     * @param caller сервис
     * @param response HTTP-ответ для заголовков
     * @return содержимое файла
     */
    @GetMapping("/internal/package/files/{fileId}")
    @PreAuthorize("hasAuthority('SVC_DOCPROC')")
    public byte[] file(@PathVariable UUID fileId, @RequestParam(required = false) Integer version,
                       CurrentUser caller, HttpServletResponse response) {
        String client = caller.getToken().getClaimAsString("azp");
        String actor = client == null ? caller.userId() : client;
        FileContent file = contents.read(fileId, version, actor, FileContentService.ACTION_INTERNAL_READ);
        response.setContentType(file.mime());
        response.setHeader("X-File-Version", String.valueOf(file.version()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return file.content();
    }
}
