package ru.subsidy.rules.api.controller.impl;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.rules.api.controller.InternalFactsController;
import ru.subsidy.rules.api.dto.FactsRs;
import ru.subsidy.rules.facts.FactsAssembler;
import ru.subsidy.rules.protocol.ProtocolQueryService;

/**
 * Реализация {@link InternalFactsController}.
 */
@RestController
public class InternalFactsControllerImpl implements InternalFactsController {

    private final FactsAssembler assembler;
    private final ProtocolQueryService protocols;

    /**
     * Конструктор.
     *
     * @param assembler факты
     * @param protocols протоколы
     */
    public InternalFactsControllerImpl(FactsAssembler assembler, ProtocolQueryService protocols) {
        this.assembler = assembler;
        this.protocols = protocols;
    }

    @Override
    @PreAuthorize("hasAuthority('SVC_ASSISTANT')")
    public FactsRs facts(UUID packageId) {
        return new FactsRs(assembler.assemble(packageId)
                .orElseThrow(() -> new DomainException(ErrorCode.PACKAGE_NOT_FOUND, "Пакет не найден")),
                protocols.findLatest(packageId).orElse(null));
    }
}
