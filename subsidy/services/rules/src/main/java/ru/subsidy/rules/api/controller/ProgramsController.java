package ru.subsidy.rules.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.ProgramRq;
import ru.subsidy.rules.api.dto.ProgramRs;

/**
 * Программы субсидирования: параметры и чек-лист (данные с версиями).
 */
@RequestMapping("/api/v1/programs")
public interface ProgramsController {

    /**
     * Все программы.
     *
     * @return программы
     */
    @GetMapping
    Items<ProgramRs> programs();

    /**
     * Программа.
     *
     * @param code код
     * @return программа
     */
    @GetMapping("/{code}")
    ProgramRs program(@PathVariable String code);

    /**
     * Новая версия программы (старший специалист).
     *
     * @param code код
     * @param request параметры и чек-лист
     * @param user старший специалист
     * @return новая версия
     */
    @PutMapping("/{code}")
    ProgramRs update(@PathVariable String code, @Valid @RequestBody ProgramRq request, CurrentUser user);
}
