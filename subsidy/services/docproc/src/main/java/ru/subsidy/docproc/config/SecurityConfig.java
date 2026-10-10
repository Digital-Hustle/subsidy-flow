package ru.subsidy.docproc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import ru.subsidy.common.security.SubsidyJwt;
import ru.subsidy.common.web.ProblemSecurity;

/**
 * Доступ к docproc: JWT Keycloak realm subsidy; /internal/** — только service accounts rules и assistant.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Цепочка фильтров.
     *
     * @param http настройка безопасности
     * @return цепочка фильтров
     * @throws Exception ошибка конфигурации
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/internal/**").hasAnyAuthority("SVC_RULES", "SVC_ASSISTANT")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(ProblemSecurity.entryPoint())
                        .accessDeniedHandler(ProblemSecurity.accessDenied()))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(ProblemSecurity.entryPoint())
                        .accessDeniedHandler(ProblemSecurity.accessDenied())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(SubsidyJwt.converter())))
                .build();
    }
}
