package ru.subsidy.rules.api.controller;

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
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.ProgramCreateRq;
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
     * Все версии программы, новые первыми.
     *
     * @param code код
     * @return версии
     */
    @GetMapping("/{code}/versions")
    Items<ProgramRs> versions(@PathVariable String code);

    /**
     * Новая программа (старший специалист); код уникален.
     *
     * @param request код, название, параметры и чек-лист
     * @param user старший специалист
     * @return версия 1
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProgramRs create(@Valid @RequestBody ProgramCreateRq request, CurrentUser user);

    /**
     * Новая версия существующей программы (старший специалист).
     *
     * @param code код
     * @param request параметры и чек-лист
     * @param user старший специалист
     * @return новая версия
     */
    @PutMapping("/{code}")
    ProgramRs update(@PathVariable String code, @Valid @RequestBody ProgramRq request, CurrentUser user);
}
