package ru.subsidy.package_.pkg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.security.ServiceTokenProvider;

/** Проверка программы через rules; новые программы не требуют релиза package. */
@Component
public class ProgramCatalogClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String BEARER = "Bearer ";
    private static final String ITEMS = "items";

    private final RestClient http;
    private final ObjectProvider<ServiceTokenProvider> tokens;

    /**
     * Конструктор.
     *
     * @param base URL rules
     * @param tokens сервисный токен package
     */
    public ProgramCatalogClient(@Value("${app.program-catalog.url:http://rules:8083}") String base,
                                ObjectProvider<ServiceTokenProvider> tokens) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(TIMEOUT);
        requests.setReadTimeout(TIMEOUT);
        this.http = RestClient.builder().baseUrl(base).requestFactory(requests).build();
        this.tokens = tokens;
    }

    /**
     * Требует существующую программу; вызывается вне транзакции записи.
     *
     * @param code код
     * @throws DomainException PROGRAM_NOT_FOUND или DEPENDENCY_UNAVAILABLE
     */
    public void require(String code) {
        ServiceTokenProvider provider = tokens.getIfAvailable();
        if (provider == null) {
            throw unavailable();
        }
        try {
            JsonNode body = http.get().uri("/internal/rules/programs/{code}", code)
                    .header(HttpHeaders.AUTHORIZATION, BEARER + provider.token()).retrieve().body(JsonNode.class);
            if (body == null || !code.equals(body.path("code").asText()) || body.path("version").asInt() < 1) {
                throw unavailable();
            }
        } catch (HttpClientErrorException.NotFound missing) {
            throw new DomainException(ErrorCode.PROGRAM_NOT_FOUND, "Программа не найдена: " + code);
        } catch (RestClientException | IllegalStateException failure) {
            throw unavailable();
        }
    }

    /**
     * Все программы (текущие версии) с чек-листом, параметрами и признаком {@code accepting}.
     *
     * @return программы из rules
     * @throws DomainException DEPENDENCY_UNAVAILABLE
     */
    public List<JsonNode> all() {
        ServiceTokenProvider provider = tokens.getIfAvailable();
        if (provider == null) {
            throw unavailable();
        }
        try {
            JsonNode body = http.get().uri("/internal/rules/programs")
                    .header(HttpHeaders.AUTHORIZATION, BEARER + provider.token()).retrieve().body(JsonNode.class);
            if (body == null || !body.path(ITEMS).isArray()) {
                throw unavailable();
            }
            List<JsonNode> items = new ArrayList<>();
            body.path(ITEMS).forEach(items::add);
            return items;
        } catch (RestClientException | IllegalStateException failure) {
            throw unavailable();
        }
    }

    private static DomainException unavailable() {
        return new DomainException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                "Каталог программ недоступен, повторите создание пакета позже");
    }
}
