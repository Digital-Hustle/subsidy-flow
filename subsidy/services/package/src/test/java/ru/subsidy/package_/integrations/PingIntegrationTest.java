package ru.subsidy.package_.integrations;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import ru.subsidy.common.security.CurrentUser;

import ru.subsidy.package_.config.AbstractIntegrationTest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ping_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void ping_withSpecialistToken_returnsRoles() throws Exception {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-1").claim("roles", List.of("SPECIALIST")).build();
        CurrentUser user = new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_SPECIALIST")));
        mockMvc.perform(get("/api/v1/ping").with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("package"))
                .andExpect(jsonPath("$.roles[0]").value("SPECIALIST"));
    }
}
