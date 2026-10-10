package ru.subsidy.assistant.integrations;

import ru.subsidy.assistant.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        // given / when / then
        mockMvc.perform(get("/api/v1/does-not-matter")).andExpect(status().isUnauthorized());
    }
}
