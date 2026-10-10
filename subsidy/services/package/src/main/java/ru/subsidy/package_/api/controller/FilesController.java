package ru.subsidy.package_.api.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.upload.FileVersionService;
import ru.subsidy.package_.upload.UploadService;
import ru.subsidy.package_.upload.UploadService.IncomingFile;
import ru.subsidy.package_.upload.UploadService.UploadResult;

/**
 * Приём файлов в пакет (FR-02).
 */
@RestController
@RequestMapping("/api/v1/packages/{packageId}/files")
public class FilesController {

    private final UploadService uploads;
    private final FileVersionService versions;

    /**
     * Конструктор.
     *
     * @param uploads приём
     * @param versions версии файлов
     */
    public FilesController(UploadService uploads, FileVersionService versions) {
        this.uploads = uploads;
        this.versions = versions;
    }

    /**
     * Загружает файлы; разбор идёт асинхронно.
     *
     * @param packageId пакет
     * @param files файлы (multipart files)
     * @param user текущий пользователь
     * @return принятые и отклонённые
     * @throws UncheckedIOException если multipart не читается
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public UploadResult upload(@PathVariable UUID packageId, @RequestPart("files") List<MultipartFile> files,
                               CurrentUser user) {
        List<IncomingFile> incoming = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            try {
                incoming.add(new IncomingFile(file.getOriginalFilename(), file.getBytes()));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return uploads.upload(packageId, incoming, user.userId());
    }

    /**
     * Замена файла новой версией (FR-65).
     *
     * @param packageId пакет
     * @param fileId файл
     * @param file новое содержимое
     * @param user текущий пользователь
     * @return принятая версия или отказ
     * @throws UncheckedIOException ошибка чтения multipart
     */
    @PostMapping(value = "/{fileId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public UploadResult replace(@PathVariable UUID packageId, @PathVariable UUID fileId,
                                @RequestPart("file") MultipartFile file, CurrentUser user) {
        try {
            return versions.replace(packageId, fileId, new IncomingFile(file.getOriginalFilename(), file.getBytes()),
                    user.userId());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Удаление файла из пакета.
     *
     * @param packageId пакет
     * @param fileId файл
     */
    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public void remove(@PathVariable UUID packageId, @PathVariable UUID fileId) {
        versions.remove(packageId, fileId);
    }
}
