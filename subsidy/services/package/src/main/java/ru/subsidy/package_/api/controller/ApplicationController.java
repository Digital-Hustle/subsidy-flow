package ru.subsidy.package_.api.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.api.dto.request.UpdateApplicationRq;
import ru.subsidy.package_.api.dto.response.ApplicationHistoryRs;
import ru.subsidy.package_.pkg.ApplicationService.Revision;

/** Контракт редактирования заявки (FR-01/47). */
@RequestMapping("/api/v1/packages/{packageId}/application")
public interface ApplicationController {

    /**
     * История заявки.
     *
     * @param packageId пакет
     * @return изменения
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')")
    @Operation(summary = "История изменений заявки")
    ApplicationHistoryRs history(@PathVariable UUID packageId);

    /**
     * Замена заявки.
     *
     * @param packageId пакет
     * @param request заявка и версия
     * @param user текущий сотрудник
     * @return новая версия
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    @Operation(summary = "Изменить заявку и запустить перепроверку")
    Revision update(@PathVariable UUID packageId, @Valid @RequestBody UpdateApplicationRq request, CurrentUser user);
}
