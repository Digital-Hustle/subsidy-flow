package ru.subsidy.rules.projection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.security.ServiceTokenProvider;
import ru.subsidy.rules.config.RulesProperties;

/**
 * Результаты разбора пакета из docproc ({@code GET /internal/docproc/packages/{id}/documents}, токен svc-rules).
 * Только для пересборки проекции (RUL-18), основной путь — события.
 */
@Component
public class DocprocClient {

    private static final String ITEMS = "items";

    private final RestClient http;
    private final ObjectProvider<ServiceTokenProvider> tokens;
    private final boolean configured;

    /**
     * Конструктор.
     *
     * @param props настройки (docprocUrl)
     * @param tokens токен сервисного аккаунта
     */
    public DocprocClient(RulesProperties props, ObjectProvider<ServiceTokenProvider> tokens) {
        String base = props.docprocUrl() == null ? "" : props.docprocUrl();
        this.http = RestClient.builder().baseUrl(base).build();
        this.tokens = tokens;
        this.configured = !base.isBlank();
    }

    /**
     * Неудалённые документы пакета с последними результатами.
     *
     * @param packageId пакет
     * @return документы в формате DocumentResult docproc
     * @throws DomainException DEPENDENCY_UNAVAILABLE, если docproc не ответил
     */
    public List<JsonNode> documents(UUID packageId) {
        if (!configured) {
            throw unavailable();
        }
        ServiceTokenProvider provider = tokens.getIfAvailable();
        JsonNode body;
        try {
            body = http.get().uri("/internal/docproc/packages/{id}/documents", packageId)
                    .headers(headers -> {
                        if (provider != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token());
                        }
                    })
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException failure) {
            throw unavailable();
        }
        if (body == null || !body.path(ITEMS).isArray()) {
            throw unavailable();
        }
        List<JsonNode> out = new ArrayList<>();
        body.path(ITEMS).forEach(out::add);
        return out;
    }

    private static DomainException unavailable() {
        return new DomainException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Сервис разбора недоступен, повторите позже");
    }
}
