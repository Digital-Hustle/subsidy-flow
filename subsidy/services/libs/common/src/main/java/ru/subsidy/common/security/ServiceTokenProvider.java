package ru.subsidy.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Токен service account (client_credentials) для вызовов /internal/** других сервисов; кэш до истечения.
 */
public class ServiceTokenProvider {

    private static final long SKEW_S = 30;
    private static final long DEFAULT_TTL_S = 60;
    private static final String ACCESS_TOKEN = "access_token";

    private final RestClient http;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    /**
     * Конструктор.
     *
     * @param tokenUri адрес token endpoint Keycloak
     * @param clientId client id (svc-*)
     * @param clientSecret секрет
     * @param clock часы
     */
    public ServiceTokenProvider(String tokenUri, String clientId, String clientSecret, Clock clock) {
        this.http = RestClient.create();
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    /**
     * Текущий access token.
     *
     * @return bearer-токен
     * @throws IllegalStateException Keycloak не выдал токен
     */
    @SuppressWarnings("unchecked")
    public synchronized String token() {
        if (token != null && clock.instant().isBefore(expiresAt)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> response = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        if (response == null || response.get(ACCESS_TOKEN) == null) {
            throw new IllegalStateException("Keycloak не выдал access_token");
        }
        token = (String) response.get(ACCESS_TOKEN);
        long ttl = ((Number) response.getOrDefault("expires_in", DEFAULT_TTL_S)).longValue();
        expiresAt = clock.instant().plusSeconds(Math.max(1, ttl - SKEW_S));
        return token;
    }
}
