package ru.subsidy.rules.api.controller;

import org.springframework.web.bind.annotation.GetMapping;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.api.dto.PingRs;

/**
 * Проверка доступа через gateway.
 */
public interface PingController {

    /**
     * Ответ для авторизованного пользователя.
     *
     * @param user текущий пользователь из JWT
     * @return сервис, пользователь и роли
     */
    @GetMapping("/api/v1/ping")
    PingRs ping(CurrentUser user);
}
