package ru.subsidy.assistant.rules;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.config.AssistantProperties;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.security.ServiceTokenProvider;

/**
 * Чтение фактов пакета из rules (`/internal/rules/packages/{id}/facts`). Только чтение.
 */
@Component
public class RulesClient {

    private final RestClient http;
    private final ObjectProvider<ServiceTokenProvider> tokens;

    /**
     * Конструктор.
     *
     * @param props настройки
     * @param tokens токен сервисного аккаунта
     */
    public RulesClient(AssistantProperties props, ObjectProvider<ServiceTokenProvider> tokens) {
        this.http = RestClient.builder().baseUrl(props.rulesUrl() == null ? "" : props.rulesUrl()).build();
        this.tokens = tokens;
    }

    /**
     * Факты и последний протокол пакета.
     *
     * @param packageId пакет
     * @return {facts, protocol}
     * @throws DomainException PACKAGE_NOT_FOUND или DEPENDENCY_UNAVAILABLE
     */
    public JsonNode facts(UUID packageId) {
        ServiceTokenProvider provider = tokens.getIfAvailable();
        JsonNode body;
        try {
            body = http.get().uri("/internal/rules/packages/{id}/facts", packageId)
                    .headers(headers -> {
                        if (provider != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token());
                        }
                    })
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException failure) {
            if (failure.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new DomainException(ErrorCode.PACKAGE_NOT_FOUND, "Пакет не найден");
            }
            throw unavailable();
        } catch (RestClientException failure) {
            throw unavailable();
        }
        if (body == null) {
            throw unavailable();
        }
        return body;
    }

    private static DomainException unavailable() {
        return new DomainException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Сервис проверок недоступен, повторите позже");
    }
}
