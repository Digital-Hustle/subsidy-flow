package ru.subsidy.rules.api.controller.impl;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.api.controller.ProgramsController;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.ProgramRq;
import ru.subsidy.rules.api.dto.ProgramRs;
import ru.subsidy.rules.program.ProgramService;

/**
 * Реализация {@link ProgramsController}.
 */
@RestController
public class ProgramsControllerImpl implements ProgramsController {

    private static final String READERS = "hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')";

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
        return new Items<>(programs.all().stream().map(ProgramRs::of).toList());
    }

    @Override
    @PreAuthorize(READERS)
    public ProgramRs program(String code) {
        return ProgramRs.of(programs.current(code));
    }

    @Override
    @PreAuthorize("hasRole('SENIOR_SPECIALIST')")
    public ProgramRs update(String code, ProgramRq request, CurrentUser user) {
        return ProgramRs.of(programs.update(code, request.name(), request.params(), request.checklist(),
                user.userId()));
    }
}
