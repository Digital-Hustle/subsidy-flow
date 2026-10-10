package ru.subsidy.common;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import ru.subsidy.common.security.IssuerJwtDecoder;

/**
 * Сессии сотрудников (ADR-023): при заданном {@code app.staff-token.jwks-uri} сервис принимает и токены package,
 * и токены Keycloak. Без настройки — стандартный декодер Spring Boot (только Keycloak).
 */
@AutoConfiguration(before = OAuth2ResourceServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "app.staff-token.jwks-uri")
public class StaffTokenAutoConfiguration {

    /**
     * Декодер двух издателей.
     *
     * @param staffIssuer issuer сессий сотрудников
     * @param staffJwks   ключи сессий
     * @param kcIssuer    issuer Keycloak
     * @param kcJwks      ключи Keycloak
     * @param audiences   допустимые aud
     * @return декодер
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.staff-token.issuer}") String staffIssuer,
                                 @Value("${app.staff-token.jwks-uri}") String staffJwks,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String kcIssuer,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String kcJwks,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.audiences:}") List<String> audiences) {
        return IssuerJwtDecoder.of(staffIssuer, staffJwks, kcIssuer, kcJwks, audiences);
    }
}
