package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.package_.config.AbstractIntegrationTest;

/**
 * Демо-стенд (STAND_MODE=demo, ADR-023): демо-сотрудники по ролям, код в ответе и общий код 000000.
 */
@TestPropertySource(properties = "STAND_MODE=demo")
class StaffDemoModeIntegrationTest extends AbstractIntegrationTest {

    private static final String AUDITOR = "auditor@demo.subsidy.local";

    @Test
    void demoUsersListed_codeShown_sharedCodeLetsIn() throws Exception {
        // given / when
        JsonNode mode = objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/mode")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode sent = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/code").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", AUDITOR)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // then
        assertThat(mode.path("mode").asText()).isEqualTo("demo");
        assertThat(mode.path("demoCode").asText()).isEqualTo("000000");
        assertThat(mode.path("demo").size()).isEqualTo(3);
        assertThat(sent.path("demoCode").asText()).matches("\\d{6}");
        mockMvc.perform(post("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "senior@demo.subsidy.local", "code", "000 000"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("SENIOR_SPECIALIST"));
        mockMvc.perform(post("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@demo.subsidy.local", "code", "000000"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOGIN_CODE_INVALID"));
    }
}
