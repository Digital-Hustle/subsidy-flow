package ru.subsidy.rules.integrations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.config.AbstractIntegrationTest;
import ru.subsidy.rules.jooq.Tables;

/**
 * Создание программы (FR-35): права, уникальность кода, валидация, версии, срок приёма.
 */
class ProgramCreateIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/programs";
    private static final String CODE = "TEST_GRANT";
    private static final String SENIOR = "SENIOR_SPECIALIST";

    @Autowired
    private DSLContext dsl;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private ObjectNode program(String code, String deadline) {
        ObjectNode body = objectMapper.createObjectNode().put("code", code).put("name", "Тестовый грант");
        ObjectNode params = body.putObject("params").put("description", "Описание для заявителя");
        if (deadline != null) {
            params.put("deadline", deadline);
        }
        ObjectNode item = body.putArray("checklist").addObject().put("item", "APPLICATION")
                .put("title", "Заявление").put("kind", "REQUIRED").put("single", true);
        item.putArray("types").add("APPLICATION");
        return body;
    }

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(Tables.PROGRAMS).where(Tables.PROGRAMS.CODE.in(CODE, "TEST_CLOSED")).execute();
    }

    @Test
    void create_seniorCreatesVersionOne_andCodeIsUnique() throws Exception {
        // given
        String body = program(CODE, null).toString();

        // when / then
        mockMvc.perform(post(URL).with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(authentication(user(SENIOR)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.accepting").value(true))
                .andExpect(jsonPath("$.params.description").value("Описание для заявителя"));
        mockMvc.perform(post(URL).with(authentication(user(SENIOR)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROGRAM_EXISTS"));
        mockMvc.perform(get(URL).with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.code == '" + CODE + "')]").exists());
    }

    @Test
    void create_rejectsBadCodeChecklistAndDeadline() throws Exception {
        // given
        ObjectNode badCode = program("bad code", null);
        ObjectNode badChecklist = program(CODE, null);
        badChecklist.putArray("checklist").addObject().put("item", "X").put("kind", "MAYBE");
        ObjectNode badDeadline = program(CODE, "31.12.2026");

        // when / then
        for (ObjectNode body : List.of(badCode, badChecklist, badDeadline)) {
            mockMvc.perform(post(URL).with(authentication(user(SENIOR)))
                            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void versions_newestFirst_andPutUnknownCodeIsRejected() throws Exception {
        // given
        ObjectNode body = program(CODE, null);
        mockMvc.perform(post(URL).with(authentication(user(SENIOR)))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated());
        body.remove("code");
        body.put("name", "Тестовый грант (изм.)");

        // when
        mockMvc.perform(put(URL + "/" + CODE).with(authentication(user(SENIOR)))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        // then
        mockMvc.perform(get(URL + "/" + CODE + "/versions").with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].version").value(2))
                .andExpect(jsonPath("$.items[1].name").value("Тестовый грант"));
        mockMvc.perform(put(URL + "/NO_SUCH").with(authentication(user(SENIOR)))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(jsonPath("$.code").value("PROGRAM_NOT_FOUND"));
        mockMvc.perform(get(URL + "/NO_SUCH/versions").with(authentication(user(SENIOR))))
                .andExpect(jsonPath("$.code").value("PROGRAM_NOT_FOUND"));
    }

    @Test
    void accepting_isFalseAfterDeadline() throws Exception {
        // given: срок приёма в прошлом
        ObjectNode body = program("TEST_CLOSED", "2020-01-01");

        // when / then
        mockMvc.perform(post(URL).with(authentication(user(SENIOR)))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepting").value(false));
    }
}
