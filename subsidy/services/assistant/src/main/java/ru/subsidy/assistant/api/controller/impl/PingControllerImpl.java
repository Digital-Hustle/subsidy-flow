package ru.subsidy.assistant.api.controller.impl;

import java.util.TreeSet;

import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.assistant.api.controller.PingController;
import ru.subsidy.assistant.api.dto.PingRs;

/**
 * Реализация {@link PingController}.
 */
@RestController
public class PingControllerImpl implements PingController {

    @Override
    public PingRs ping(CurrentUser user) {
        return new PingRs("assistant", user.userId(), new TreeSet<>(user.roles()));
    }
}
