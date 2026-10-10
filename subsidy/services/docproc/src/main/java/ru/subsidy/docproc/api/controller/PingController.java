package ru.subsidy.docproc.api.controller;

import org.springframework.web.bind.annotation.GetMapping;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.docproc.api.dto.PingRs;

/**
 * Проверка доступа через gateway: кто я и какие роли видит сервис.
 */
public interface PingController {

    /**
     * Ответ для авторизованного пользователя.
     *
     * @param user текущий пользователь из JWT
     * @return сервис, пользователь и его роли
     */
    @GetMapping("/api/v1/ping")
    PingRs ping(CurrentUser user);
}
