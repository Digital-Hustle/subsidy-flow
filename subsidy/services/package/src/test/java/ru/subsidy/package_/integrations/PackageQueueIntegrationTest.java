package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;

class PackageQueueIntegrationTest extends AbstractIntegrationTest {

    private static final int[] WEIGHTS = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final SecureRandom RANDOM = new SecureRandom();

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static String randomInn() {
        StringBuilder inn = new StringBuilder("77");
        while (inn.length() < 9) {
            inn.append(RANDOM.nextInt(10));
        }
        int sum = 0;
        for (int idx = 0; idx < 9; idx++) {
            sum += WEIGHTS[idx] * (inn.charAt(idx) - '0');
        }
        return inn.append(sum % 11 % 10).toString();
    }

    private String create(String inn) throws Exception {
        String body = mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"" + inn + "\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.packageId");
    }

    @Test
    void queue_pagesWithCursor_newestFirst() throws Exception {
        String inn = randomInn();
        List<String> created = new ArrayList<>();
        for (int idx = 0; idx < 3; idx++) {
            created.add(create(inn));
        }
        String first = mockMvc.perform(get("/api/v1/packages").param("inn", inn).param("limit", "2")
                        .with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page.total").value(3))
                .andExpect(jsonPath("$.items[0].id").value(created.get(2)))
                .andExpect(jsonPath("$.items[0].org.inn").value(inn))
                .andExpect(jsonPath("$.items[0].status").value("COLLECTING"))
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(first, "$.page.next");
        assertThat(next).isNotBlank();
        mockMvc.perform(get("/api/v1/packages").param("inn", inn).param("limit", "2").param("cursor", next)
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(created.get(0)))
                .andExpect(jsonPath("$.page.next").doesNotExist());
    }

    @Test
    void queue_filtersByStatus_andRejectsBrokenCursor() throws Exception {
        String inn = randomInn();
        create(inn);
        mockMvc.perform(get("/api/v1/packages").param("inn", inn).param("status", "DECIDED")
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.total").value(0));
        mockMvc.perform(get("/api/v1/packages").param("cursor", "!!не-курсор")
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().is4xxClientError());
    }
}
