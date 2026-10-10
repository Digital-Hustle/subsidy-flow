package ru.subsidy.package_.integrations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;

class PackagesIntegrationTest extends AbstractIntegrationTest {

    private static final String INN = "7707083893";
    private static final String BODY = "{\"orgInn\":\"" + INN + "\",\"programCode\":\"INNOVATION\"}";

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void createPackage_bySpecialist_returns201AndIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COLLECTING"))
                .andExpect(jsonPath("$.org.inn").value(INN))
                .andReturn().getResponse().getContentAsString();
        String packageId = JsonPath.read(first, "$.packageId");
        mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.packageId").value(packageId));
        mockMvc.perform(get("/api/v1/packages/" + packageId).with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void createPackage_invalidInn_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083894\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INN_INVALID"));
    }

    @Test
    void createPackage_byAuditor_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/packages").with(authentication(user("AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPackage_unknownId_returns404_withoutToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/packages/" + UUID.randomUUID()).with(authentication(user("SPECIALIST"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/packages/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void organization_lookupAndCreate() throws Exception {
        mockMvc.perform(post("/api/v1/organizations").with(authentication(user("SENIOR_SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inn\":\"500100732259\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/organizations").param("inn", "500100732259")
                        .with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inn").value("500100732259"));
    }
}
