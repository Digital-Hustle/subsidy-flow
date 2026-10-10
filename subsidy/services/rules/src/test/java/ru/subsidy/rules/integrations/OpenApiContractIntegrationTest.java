package ru.subsidy.rules.integrations;

import ru.subsidy.rules.config.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Выгружает OpenAPI в contracts/openapi/rules.json (gradle :rules:exportOpenApi). CI сверяет git diff.
 */
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApi_isServedAndExported() throws Exception {
        // given
        String dir = System.getProperty("contract.dir");

        // when
        String json = mockMvc.perform(get("/v3/api-docs").with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        if (dir != null) {
            Object tree = objectMapper.readValue(json, Object.class);
            Files.createDirectories(Path.of(dir));
            Files.writeString(Path.of(dir, "rules.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n");
        }
    }
}
