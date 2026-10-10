package ru.subsidy.docproc.api.controller.impl;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.docproc.api.controller.InternalDocumentsController;
import ru.subsidy.docproc.api.dto.Items;
import ru.subsidy.docproc.result.ResultService;
import ru.subsidy.docproc.result.ResultService.DocumentResult;

/**
 * Реализация {@link InternalDocumentsController}.
 */
@RestController
public class InternalDocumentsControllerImpl implements InternalDocumentsController {

    private final ResultService results;

    /**
     * Конструктор.
     *
     * @param results результаты
     */
    public InternalDocumentsControllerImpl(ResultService results) {
        this.results = results;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('SVC_RULES','SVC_ASSISTANT')")
    public Items<DocumentResult> documents(UUID packageId) {
        return new Items<>(results.byPackage(packageId));
    }
}
