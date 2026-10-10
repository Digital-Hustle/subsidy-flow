package ru.subsidy.package_.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.api.controller.AuthController.UserRs;
import ru.subsidy.package_.staff.StaffRole;
import ru.subsidy.package_.staff.StaffService;
import ru.subsidy.package_.staff.StaffUser;

/**
 * Сотрудники и роли (FR-60, ADR-023): видит и меняет только старший специалист.
 */
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasRole('SENIOR_SPECIALIST')")
public class StaffController {

    private static final int NAME_MAX = 120;

    private final StaffService staff;

    /**
     * Конструктор.
     *
     * @param staff сервис сотрудников
     */
    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    /**
     * Все сотрудники.
     *
     * @return список
     */
    @GetMapping
    public StaffListRs list() {
        return new StaffListRs(staff.list().stream().map(UserRs::of).toList());
    }

    /**
     * Приглашает сотрудника по почте.
     *
     * @param request имя, почта, роль
     * @param user    кто приглашает
     * @return сотрудник; 409 CONFLICT — почта занята
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRs invite(@Valid @RequestBody InviteRq request, CurrentUser user) {
        return UserRs.of(staff.invite(user.fullName(), request.name(), request.email(), request.role()));
    }

    /**
     * Меняет роль или включает/отключает сотрудника.
     *
     * @param id      сотрудник
     * @param request новая роль и/или активность
     * @return сотрудник; 404 NOT_FOUND, 409 STAFF_LAST_SENIOR
     */
    @PatchMapping("/{id}")
    public UserRs update(@PathVariable UUID id, @RequestBody UpdateRq request) {
        StaffUser result = null;
        if (request.role() != null) {
            result = staff.setRole(id, request.role());
        }
        if (request.active() != null) {
            result = staff.setActive(id, request.active());
        }
        return UserRs.of(result != null ? result : staff.get(id));
    }

    /**
     * Список.
     *
     * @param items сотрудники
     */
    public record StaffListRs(List<UserRs> items) {
    }

    /**
     * Приглашение.
     *
     * @param name  имя
     * @param email почта
     * @param role  роль
     */
    public record InviteRq(@NotBlank @Size(max = NAME_MAX) String name, @NotBlank @Email String email, @NotNull StaffRole role) {
    }

    /**
     * Изменение.
     *
     * @param role   новая роль или null
     * @param active включить/отключить или null
     */
    public record UpdateRq(StaffRole role, Boolean active) {
    }
}
