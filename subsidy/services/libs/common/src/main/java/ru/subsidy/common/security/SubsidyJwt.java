package ru.subsidy.common.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT realm subsidy → CurrentUser: роли из claim roles (ROLE_*), service account svc-x → SVC_X.
 */
public final class SubsidyJwt {

    private static final String ROLES_CLAIM = "roles";
    private static final String CLIENT_CLAIM = "azp";
    private static final String SERVICE_PREFIX = "svc-";

    private SubsidyJwt() {
    }

    /**
     * Конвертер для oauth2ResourceServer (не регистрировать как @Bean: Kafka собирает все Converter-бины).
     *
     * @return конвертер
     */
    public static Converter<Jwt, AbstractAuthenticationToken> converter() {
        return jwt -> new CurrentUser(jwt, authorities(jwt));
    }

    /**
     * Полномочия токена.
     *
     * @param jwt токен
     * @return ROLE_* и SVC_*
     */
    public static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> result = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles != null) {
            roles.forEach(role -> result.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        String client = jwt.getClaimAsString(CLIENT_CLAIM);
        if (client != null && client.startsWith(SERVICE_PREFIX)) {
            result.add(new SimpleGrantedAuthority("SVC_"
                    + client.substring(SERVICE_PREFIX.length()).toUpperCase(Locale.ROOT)));
        }
        return result;
    }
}
