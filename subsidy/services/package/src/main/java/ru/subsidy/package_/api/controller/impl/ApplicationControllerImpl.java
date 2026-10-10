package ru.subsidy.package_.api.controller.impl;

import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.api.controller.ApplicationController;
import ru.subsidy.package_.api.dto.request.UpdateApplicationRq;
import ru.subsidy.package_.api.dto.response.ApplicationHistoryRs;
import ru.subsidy.package_.pkg.ApplicationService;
import ru.subsidy.package_.pkg.ApplicationService.Revision;

/** HTTP-адаптер заявки. */
@RestController
public class ApplicationControllerImpl implements ApplicationController {

    private final ApplicationService applications;

    /**
     * Конструктор.
     *
     * @param applications заявки
     */
    public ApplicationControllerImpl(ApplicationService applications) {
        this.applications = applications;
    }

    @Override
    public ApplicationHistoryRs history(UUID packageId) {
        return new ApplicationHistoryRs(applications.history(packageId));
    }

    @Override
    public Revision update(UUID packageId, UpdateApplicationRq request, CurrentUser user) {
        return applications.update(packageId, request.expectedRevision(), request.application(), user.userId());
    }
}
