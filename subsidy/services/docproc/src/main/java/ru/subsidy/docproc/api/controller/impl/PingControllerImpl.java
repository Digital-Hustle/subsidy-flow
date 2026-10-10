package ru.subsidy.docproc.api.controller.impl;

import java.util.TreeSet;

import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.docproc.api.controller.PingController;
import ru.subsidy.docproc.api.dto.PingRs;

/**
 * Реализация {@link PingController}.
 */
@RestController
public class PingControllerImpl implements PingController {

    @Override
    public PingRs ping(CurrentUser user) {
        return new PingRs("docproc", user.userId(), new TreeSet<>(user.roles()));
    }
}
