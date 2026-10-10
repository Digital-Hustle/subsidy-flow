package ru.subsidy.package_.api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.package_.staff.TokenIssuer;

/**
 * Ключи проверки токенов сотрудников для сервисов (ADR-023). Только внутренняя сеть: снаружи /internal закрыт в Caddy и gateway.
 */
@RestController
public class StaffJwksController {

    private final TokenIssuer issuer;

    /**
     * Конструктор.
     *
     * @param issuer выпуск токенов
     */
    public StaffJwksController(TokenIssuer issuer) {
        this.issuer = issuer;
    }

    /**
     * JWKS.
     *
     * @return {@code {"keys": [...]}}
     */
    @GetMapping("/internal/staff/jwks")
    public Map<String, List<Map<String, Object>>> jwks() {
        return Map.of("keys", issuer.publicKeys());
    }
}
