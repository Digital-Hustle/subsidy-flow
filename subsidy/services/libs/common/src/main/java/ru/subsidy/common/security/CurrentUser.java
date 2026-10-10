package ru.subsidy.common.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Текущий пользователь из JWT (Keycloak или device-токен).
 * Контроллеры получают его через @AuthenticationPrincipal CurrentUser.
 */
public class CurrentUser extends JwtAuthenticationToken {

    private final String userId;
    private final String fullName;
    private final Set<String> roles;

    public CurrentUser(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(jwt, authorities, jwt.getSubject());
        this.userId = jwt.getSubject();
        this.fullName = jwt.getClaimAsString("name");
        this.roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public String userId() {
        return userId;
    }

    public String fullName() {
        return fullName;
    }

    public Set<String> roles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isSpecialist() {
        return hasRole("SPECIALIST");
    }

    public boolean isSenior() {
        return hasRole("SENIOR_SPECIALIST");
    }

    public boolean isAuditor() {
        return hasRole("AUDITOR");
    }

    @Override
    public String toString() {
        return "CurrentUser{userId='%s', roles=%s}".formatted(userId, roles);
    }
}
