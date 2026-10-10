package ru.subsidy.package_.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import ru.subsidy.common.exception.NotFoundException;
import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.organization.OrganizationService;
import ru.subsidy.package_.organization.OrganizationService.Organization;

/**
 * Организации по ИНН (FR-01).
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationsController {

    private final OrganizationService organizations;

    /**
     * Конструктор.
     *
     * @param organizations сервис организаций
     */
    public OrganizationsController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    /**
     * Организация по ИНН.
     *
     * @param inn ИНН
     * @return организация
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')")
    public OrganizationResponse find(@RequestParam String inn) {
        return organizations.findByInn(inn).map(OrganizationResponse::of)
                .orElseThrow(() -> new NotFoundException("Организация не найдена"));
    }

    /**
     * Регистрирует организацию.
     *
     * @param request ИНН
     * @param user текущий пользователь
     * @return организация
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request, CurrentUser user) {
        return OrganizationResponse.of(organizations.getOrCreate(request.inn(), user.userId()));
    }

    /**
     * Запрос создания.
     *
     * @param inn ИНН
     */
    public record CreateOrganizationRequest(@NotBlank String inn) {
    }

    /**
     * Ответ.
     *
     * @param id id
     * @param inn ИНН
     * @param name наименование
     */
    public record OrganizationResponse(UUID id, String inn, String name) {

        static OrganizationResponse of(Organization org) {
            return new OrganizationResponse(org.id(), org.inn(), org.name());
        }
    }
}
