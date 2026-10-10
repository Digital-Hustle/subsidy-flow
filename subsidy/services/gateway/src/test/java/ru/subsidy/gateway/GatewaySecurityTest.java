package ru.subsidy.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient web;

    @Test
    void specialistApiWithoutTokenIs401() {
        for (String svc : new String[] {"package", "docproc", "rules", "assistant"}) {
            web.get().uri("/" + svc + "/api/v1/ping").exchange().expectStatus().isUnauthorized();
        }
    }

    @Test
    void loginByCodeIsOpenWithoutToken() {
        // ADR-023: до входа токена нет — gateway пропускает к package (тот недоступен в тесте → 5xx, но не 401)
        for (String step : new String[] {"mode", "code", "verify", "refresh", "logout"}) {
            web.post().uri("/package/api/v1/auth/" + step).exchange()
                    .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
        }
        web.get().uri("/package/api/v1/auth/me").exchange().expectStatus().isUnauthorized();
        web.get().uri("/package/api/v1/staff").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void internalIsClosed() {
        web.get().uri("/package/internal/staff/jwks").exchange().expectStatus().is4xxClientError();
        web.get().uri("/package/internal/package/files/1").exchange().expectStatus().is4xxClientError();
        web.get().uri("/unknown/api/v1/x").exchange().expectStatus().is4xxClientError();
    }
}
