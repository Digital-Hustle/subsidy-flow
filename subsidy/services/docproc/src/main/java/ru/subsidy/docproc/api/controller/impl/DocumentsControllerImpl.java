package ru.subsidy.docproc.api.controller.impl;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.docproc.api.controller.DocumentsController;
import ru.subsidy.docproc.api.dto.Items;
import ru.subsidy.docproc.api.dto.TypeRequest;
import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.docproc.classify.TypeRegistry;
import ru.subsidy.docproc.result.ResultService;
import ru.subsidy.docproc.result.ResultService.DocumentResult;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmStatus;

/**
 * Реализация {@link DocumentsController}.
 */
@RestController
public class DocumentsControllerImpl implements DocumentsController {

    private static final String READERS = "hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')";

    private final ResultService results;
    private final TypeRegistry registry;
    private final LlmGateway llm;

    /**
     * Конструктор.
     *
     * @param results результаты
     * @param registry реестр видов
     * @param llm модель
     */
    public DocumentsControllerImpl(ResultService results, TypeRegistry registry, LlmGateway llm) {
        this.results = results;
        this.registry = registry;
        this.llm = llm;
    }

    @Override
    @PreAuthorize(READERS)
    public DocumentResult result(UUID documentId) {
        return results.result(documentId);
    }

    @Override
    @PreAuthorize(READERS)
    public Items<DocumentResult> byPackage(UUID packageId) {
        return new Items<>(results.byPackage(packageId));
    }

    @Override
    @PreAuthorize(READERS)
    public Items<DocType> docTypes() {
        return new Items<>(registry.all());
    }

    @Override
    @PreAuthorize(READERS)
    public LlmStatus modelStatus() {
        return llm.status();
    }

    @Override
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public void overrideType(UUID documentId, TypeRequest request, CurrentUser user) {
        results.overrideType(documentId, request.type(), request.reason(), user.userId());
    }

    @Override
    @PreAuthorize("hasRole('SENIOR_SPECIALIST')")
    public void reprocess(UUID fileId) {
        results.reprocess(fileId);
    }
}
