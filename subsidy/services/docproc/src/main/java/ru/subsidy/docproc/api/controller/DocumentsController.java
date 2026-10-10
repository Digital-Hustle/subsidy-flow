package ru.subsidy.docproc.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.docproc.api.dto.Items;
import ru.subsidy.docproc.api.dto.TypeRequest;
import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.docproc.result.ResultService.DocumentResult;
import ru.subsidy.llm.api.LlmStatus;

/**
 * Результаты разбора, смена вида, переразбор, статус модели, реестр видов.
 */
@RequestMapping("/api/v1")
public interface DocumentsController {

    /**
     * Результат документа.
     *
     * @param documentId документ
     * @return вид, поля, проверки
     */
    @GetMapping("/documents/{documentId}/result")
    DocumentResult result(@PathVariable UUID documentId);

    /**
     * Документы пакета.
     *
     * @param packageId пакет
     * @return документы
     */
    @GetMapping("/packages/{packageId}/documents")
    Items<DocumentResult> byPackage(@PathVariable UUID packageId);

    /**
     * Реестр видов.
     *
     * @return виды и схемы полей
     */
    @GetMapping("/doc-types")
    Items<DocType> docTypes();

    /**
     * Статус модели.
     *
     * @return провайдер, модель, доступность
     */
    @GetMapping("/model/status")
    LlmStatus modelStatus();

    /**
     * Смена вида документа специалистом (FR-29).
     *
     * @param documentId документ
     * @param request вид и причина
     * @param user специалист
     */
    @PutMapping("/documents/{documentId}/type")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void overrideType(@PathVariable UUID documentId, @Valid @RequestBody TypeRequest request, CurrentUser user);

    /**
     * Переразбор файла с начала.
     *
     * @param fileId файл
     */
    @PostMapping("/files/{fileId}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void reprocess(@PathVariable UUID fileId);
}
