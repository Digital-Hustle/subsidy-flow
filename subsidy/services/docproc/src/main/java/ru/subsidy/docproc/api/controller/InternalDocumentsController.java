package ru.subsidy.docproc.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import ru.subsidy.docproc.api.dto.Items;
import ru.subsidy.docproc.result.ResultService.DocumentResult;

/**
 * Внутренний доступ к результатам пакета для rules и assistant.
 */
public interface InternalDocumentsController {

    /**
     * Все документы пакета.
     *
     * @param packageId пакет
     * @return документы
     */
    @GetMapping("/internal/docproc/packages/{packageId}/documents")
    Items<DocumentResult> documents(@PathVariable UUID packageId);
}
