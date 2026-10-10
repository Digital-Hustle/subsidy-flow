package ru.subsidy.rules.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import ru.subsidy.rules.api.dto.ProgramLookupRs;

/** Каталог программ для приёма новых пакетов. */
public interface InternalProgramsController {

    /**
     * Текущая версия существующей программы.
     *
     * @param code код
     * @return код и версия
     */
    @GetMapping("/internal/rules/programs/{code}")
    ProgramLookupRs program(@PathVariable String code);
}
