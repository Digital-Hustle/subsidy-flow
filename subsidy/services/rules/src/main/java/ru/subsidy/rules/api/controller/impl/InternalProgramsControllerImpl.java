package ru.subsidy.rules.api.controller.impl;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.rules.api.controller.InternalProgramsController;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.ProgramLookupRs;
import ru.subsidy.rules.api.dto.ProgramRs;
import ru.subsidy.rules.program.ProgramService;

/** Сервисный API package → rules. */
@RestController
public class InternalProgramsControllerImpl implements InternalProgramsController {

    private final ProgramService programs;

    /**
     * Конструктор.
     *
     * @param programs программы
     */
    public InternalProgramsControllerImpl(ProgramService programs) {
        this.programs = programs;
    }

    @Override
    @PreAuthorize("hasAuthority('SVC_PACKAGE')")
    public ProgramLookupRs program(String code) {
        var program = programs.current(code);
        return new ProgramLookupRs(program.code(), program.version());
    }

    @Override
    @PreAuthorize("hasAuthority('SVC_PACKAGE')")
    public Items<ProgramRs> programs() {
        return new Items<>(programs.all().stream().map(program -> ProgramRs.of(program, programs.accepting(program))).toList());
    }
}
