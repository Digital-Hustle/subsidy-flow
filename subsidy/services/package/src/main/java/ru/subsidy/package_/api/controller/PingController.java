package ru.subsidy.package_.api.controller;

import java.util.Map;
import java.util.TreeSet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;

/**
 * Проверка доступа через gateway: кто я и какие роли видит сервис.
 */
@RestController
public class PingController {

    /**
     * Ответ для авторизованного пользователя.
     *
     * @param user текущий пользователь из JWT (сам Authentication, не principal)
     * @return сервис, пользователь и его роли
     */
    @GetMapping("/api/v1/ping")
    public Map<String, Object> ping(CurrentUser user) {
        return Map.of("service", "package", "userId", user.userId(), "roles", new TreeSet<>(user.roles()));
    }
}
