package ru.subsidy.package_.integrations;

import ru.subsidy.package_.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        // given / when / then
        mockMvc.perform(get("/api/v1/does-not-matter"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.hint").isNotEmpty());
    }
}
