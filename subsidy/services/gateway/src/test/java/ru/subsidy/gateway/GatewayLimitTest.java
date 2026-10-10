package ru.subsidy.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.limits.uploads-per-minute=2", "app.limits.questions-per-minute=1", "app.limits.body-max-kb=1", "app.limits.logins-per-minute=2",
    "PACKAGE_URL=http://127.0.0.1:1", "ASSISTANT_URL=http://127.0.0.1:1", "RULES_URL=http://127.0.0.1:1"})
@AutoConfigureWebTestClient
class GatewayLimitTest {

    @Autowired
    private WebTestClient web;

    private WebTestClient.ResponseSpec upload(String user) {
        return web.mutateWith(mockJwt().jwt(jwt -> jwt.subject(user))).post().uri("/package/api/v1/packages/p1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue("x").exchange();
    }

    @Test
    void uploadsOverLimitAre429WithRetryAfter() {
        upload("u1").expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
        upload("u1").expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
        upload("u1").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists("Retry-After")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
        upload("u2").expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
    }

    @Test
    void loginAttemptsAreLimitedPerClientAddress() {
        for (int attempt = 0; attempt < 2; attempt++) {
            web.post().uri("/package/api/v1/auth/code").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
        }
        web.post().uri("/package/api/v1/auth/verify").header("X-Forwarded-For", "203.0.113.7").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS).expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
        web.post().uri("/package/api/v1/auth/code").header("X-Forwarded-For", "203.0.113.8").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
    }

    @Test
    void assistantQuestionsAreLimited() {
        WebTestClient user = web.mutateWith(mockJwt().jwt(jwt -> jwt.subject("asker")));
        user.post().uri("/assistant/api/v1/packages/p1/ask").bodyValue("{}").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
        user.post().uri("/assistant/api/v1/packages/p1/ask").bodyValue("{}").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void bigBodyIs413() {
        web.mutateWith(mockJwt()).post().uri("/rules/api/v1/x").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("\"" + "a".repeat(2048) + "\"").exchange()
                .expectStatus().isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
                .expectBody().jsonPath("$.code").isEqualTo("PAYLOAD_TOO_LARGE");
    }
}
