package ru.subsidy.rules.api.controller.impl;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.api.controller.ProgramsController;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.ProgramCreateRq;
import ru.subsidy.rules.api.dto.ProgramRq;
import ru.subsidy.rules.api.dto.ProgramRs;
import ru.subsidy.rules.program.Program;
import ru.subsidy.rules.program.ProgramService;

/**
 * Реализация {@link ProgramsController}.
 */
@RestController
public class ProgramsControllerImpl implements ProgramsController {

    private static final String READERS = "hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')";
    private static final String SENIOR = "hasRole('SENIOR_SPECIALIST')";

    private final ProgramService programs;

    /**
     * Конструктор.
     *
     * @param programs программы
     */
    public ProgramsControllerImpl(ProgramService programs) {
        this.programs = programs;
    }

    @Override
    @PreAuthorize(READERS)
    public Items<ProgramRs> programs() {
        return new Items<>(programs.all().stream().map(this::rs).toList());
    }

    @Override
    @PreAuthorize(READERS)
    public ProgramRs program(String code) {
        return rs(programs.current(code));
    }

    @Override
    @PreAuthorize(READERS)
    public Items<ProgramRs> versions(String code) {
        return new Items<>(programs.versions(code).stream().map(this::rs).toList());
    }

    @Override
    @PreAuthorize(SENIOR)
    public ProgramRs create(ProgramCreateRq request, CurrentUser user) {
        return rs(programs.create(request.code(), request.name(), request.params(), request.checklist(),
                user.userId()));
    }

    @Override
    @PreAuthorize(SENIOR)
    public ProgramRs update(String code, ProgramRq request, CurrentUser user) {
        return rs(programs.update(code, request.name(), request.params(), request.checklist(), user.userId()));
    }

    private ProgramRs rs(Program program) {
        return ProgramRs.of(program, programs.accepting(program));
    }
}
