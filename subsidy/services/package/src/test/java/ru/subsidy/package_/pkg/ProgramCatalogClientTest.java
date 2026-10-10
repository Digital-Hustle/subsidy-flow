package ru.subsidy.package_.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.security.ServiceTokenProvider;

class ProgramCatalogClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private ProgramCatalogClient client(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/rules/programs/NEW_PROGRAM", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer service-token");
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ServiceTokenProvider token = mock(ServiceTokenProvider.class);
        when(token.token()).thenReturn("service-token");
        ObjectProvider<ServiceTokenProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(token);
        return new ProgramCatalogClient("http://127.0.0.1:" + server.getAddress().getPort(), provider);
    }

    @Test
    void acceptsProgramOutsideOriginalFixedList() throws IOException {
        client(200, "{\"code\":\"NEW_PROGRAM\",\"version\":1}").require("NEW_PROGRAM");
    }

    @Test
    void missingProgramIs404_andDependencyFailureIs503() throws IOException {
        assertThatThrownBy(() -> client(404, "{}").require("NEW_PROGRAM")).isInstanceOfSatisfying(
                DomainException.class, failure -> assertThat(failure.code()).isEqualTo(ErrorCode.PROGRAM_NOT_FOUND));
        server.stop(0);
        assertThatThrownBy(() -> client(503, "{}").require("NEW_PROGRAM")).isInstanceOfSatisfying(
                DomainException.class, failure -> assertThat(failure.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void malformedSuccessfulResponseIsNotAccepted() throws IOException {
        assertThatThrownBy(() -> client(200, "{\"code\":\"OTHER\",\"version\":1}").require("NEW_PROGRAM"))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }
}
