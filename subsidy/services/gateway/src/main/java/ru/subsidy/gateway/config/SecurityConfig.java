package ru.subsidy.gateway.config;

import java.text.ParseException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;

import com.nimbusds.jwt.JWTParser;

import reactor.core.publisher.Mono;

/**
 * Безопасность на входе (FR-60, rules.md §2).
 * SSE по одноразовому билету проходит к сервису, который проверяет билет сам;
 * вход сотрудника по коду — без токена; всё остальное — только с валидным JWT (Keycloak или сессия сотрудника, ADR-023);
 * внутренние ручки наружу не видны.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Цепочка фильтров.
     *
     * @param http настройка безопасности WebFlux
     * @return цепочка фильтров
     */
    @Bean
    public SecurityWebFilterChain chain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .headers(headers -> headers.frameOptions(
                        frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)))
                .authorizeExchange(auth -> auth
                        .pathMatchers("/*/internal/**", "/internal/**").denyAll()
                        .pathMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/*/api/v1/stream", "/*/api/v1/stream/events").permitAll()
                        // ADR-023: вход сотрудника по коду из письма — до токена
                        .pathMatchers("/package/api/v1/auth/mode", "/package/api/v1/auth/code", "/package/api/v1/auth/verify",
                                "/package/api/v1/auth/refresh", "/package/api/v1/auth/logout").permitAll()
                        .pathMatchers("/package/**", "/docproc/**", "/rules/**", "/assistant/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .build();
    }

    /**
     * Два издателя (ADR-023): Keycloak (service accounts) и package (сессии сотрудников). JWKS — из внутренней сети,
     * издатель выбирается по {@code iss}; токен сотрудника с {@code azp} отклоняется.
     *
     * @param jwks        адрес ключей Keycloak
     * @param issuer      issuer Keycloak
     * @param staffJwks   адрес ключей сессий сотрудников
     * @param staffIssuer issuer сессий сотрудников
     * @return декодер JWT; у сессий сотрудников подпись ES256 — алгоритм задан явно (по умолчанию Nimbus ждёт только RS256)
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks,
                                         @Value("${app.oidc.issuer}") String issuer,
                                         @Value("${app.staff-token.jwks-uri}") String staffJwks,
                                         @Value("${app.staff-token.issuer}") String staffIssuer) {
        NimbusReactiveJwtDecoder keycloak = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        keycloak.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        NimbusReactiveJwtDecoder staff = NimbusReactiveJwtDecoder.withJwkSetUri(staffJwks)
                .jwsAlgorithm(SignatureAlgorithm.ES256).build();
        staff.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(staffIssuer),
                new JwtClaimValidator<Object>("azp", azp -> azp == null)));
        return token -> {
            String iss;
            try {
                iss = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            } catch (ParseException error) {
                return Mono.error(new BadJwtException("Токен не читается", error));
            }
            return staffIssuer.equals(iss) ? staff.decode(token) : keycloak.decode(token);
        };
    }
}
