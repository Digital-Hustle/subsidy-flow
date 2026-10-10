package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.pipeline.PipelineEventsService;

class ApplicationIntegrationTest extends AbstractIntegrationTest {

    private static final String APPLICATION = """
            {"orgInn":"7707083893","orgName":"ООО Тест","ogrn":"1027700132195",
             "address":{"value":"Москва, ул. Тестовая, 1","purpose":"PROJECT"},
             "project":"Тестовый проект","requestedAmount":"7000.50","applicationDate":"2026-10-10",
             "signatory":{"fullName":"Тестовый Подписант","position":"Директор","authority":"Устав"},
             "property":{"cadastralNumber":"77:01:0001001:1","address":"Москва, участок 1","rightType":"LEASE"}}
            """;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private PipelineEventsService pipeline;

    private static CurrentUser user(String role) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("author-" + role).build();
        return new CurrentUser(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private UUID create() throws Exception {
        String response = mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\",\"application\":"
                                + APPLICATION + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.applicationRevision").value(1))
                .andExpect(jsonPath("$.application.requestedAmount").value("7000.50"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.packageId"));
    }

    private String updateBody(int revision) throws Exception {
        ObjectNode application = (ObjectNode) objectMapper.readTree(APPLICATION);
        application.put("project", "Исправленный проект");
        application.remove("property");
        return objectMapper.writeValueAsString(Map.of("expectedRevision", revision, "application", application));
    }

    @Test
    void createAndEdit_keepsSnapshotsAuthorAndOutbox() throws Exception {
        UUID id = create();
        mockMvc.perform(put("/api/v1/packages/" + id + "/application").with(authentication(user("SENIOR_SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationRevision").value(2));
        mockMvc.perform(get("/api/v1/packages/" + id).with(authentication(user("AUDITOR"))))
                .andExpect(jsonPath("$.application.project").value("Исправленный проект"))
                .andExpect(jsonPath("$.org.name").value("ООО Тест"));
        mockMvc.perform(get("/api/v1/packages/" + id + "/application/history").with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].changedBy").value("author-SENIOR_SPECIALIST"))
                .andExpect(jsonPath("$.items[0].changedAt").exists())
                .andExpect(jsonPath("$.items[0].before.project").value("Тестовый проект"))
                .andExpect(jsonPath("$.items[0].after.project").value("Исправленный проект"));
        assertThat(dsl.fetchCount(Tables.OUTBOX, Tables.OUTBOX.MESSAGE_KEY.eq(id.toString()))).isEqualTo(3);
        assertThatThrownBy(() -> dsl.update(Tables.APPLICATION_CHANGES).set(Tables.APPLICATION_CHANGES.CHANGED_BY, "spoof")
                .where(Tables.APPLICATION_CHANGES.PACKAGE_ID.eq(id)).execute()).hasMessageContaining("append-only");
    }

    @Test
    void staleVersion_decidedPackageAndUnchangedRetry() throws Exception {
        UUID id = create();
        String url = "/api/v1/packages/" + id + "/application";
        mockMvc.perform(put(url).with(authentication(user("SPECIALIST"))).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0))).andExpect(status().isConflict());
        mockMvc.perform(put(url).with(authentication(user("SPECIALIST"))).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedRevision", 1,
                                "application", objectMapper.readTree(APPLICATION)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationRevision").value(1));
        assertThat(dsl.fetchCount(Tables.APPLICATION_CHANGES, Tables.APPLICATION_CHANGES.PACKAGE_ID.eq(id))).isEqualTo(1);
        dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.STATUS, "DECIDED").where(Tables.PACKAGES.ID.eq(id)).execute();
        mockMvc.perform(put(url).with(authentication(user("SPECIALIST"))).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(1))).andExpect(status().isConflict());
    }

    @Test
    void endpoints_enforceRolesAuthenticationAndUnknownIds() throws Exception {
        String url = "/api/v1/packages/" + UUID.randomUUID() + "/application";
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(updateBody(0)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).with(authentication(user("AUDITOR"))).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0))).andExpect(status().isForbidden());
        mockMvc.perform(put(url).with(authentication(user("SPECIALIST"))).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0))).andExpect(status().isNotFound());
        mockMvc.perform(get(url + "/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url + "/history").with(authentication(user("AUDITOR")))).andExpect(status().isNotFound());
    }

    @Test
    void invalidApplication_rejectedBeforeSaving() throws Exception {
        UUID id = create();
        String body = updateBody(1).replace("7000.50", "-1.00");
        mockMvc.perform(put("/api/v1/packages/" + id + "/application").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(dsl.fetchCount(Tables.APPLICATION_CHANGES, Tables.APPLICATION_CHANGES.PACKAGE_ID.eq(id))).isEqualTo(1);
    }

    @Test
    void applicationCanCompleteLegacyDraftAndDoesNotRenameOtherPackages() throws Exception {
        UUID existing = create();
        String response = mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.applicationRevision").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.packageId");
        String update = updateBody(0).replace("ООО Тест", "ООО Новое имя");
        mockMvc.perform(put("/api/v1/packages/" + id + "/application").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationRevision").value(1));
        mockMvc.perform(get("/api/v1/packages/" + id).with(authentication(user("SPECIALIST"))))
                .andExpect(jsonPath("$.org.name").value("ООО Новое имя"));
        mockMvc.perform(get("/api/v1/packages/" + existing).with(authentication(user("SPECIALIST"))))
                .andExpect(jsonPath("$.org.name").value("ООО Тест"));
    }

    @Test
    void oldProtocolCannotConfirmNewApplicationRevision() throws Exception {
        UUID id = create();
        ObjectNode checked = objectMapper.createObjectNode().put("packageId", id.toString())
                .put("protocolId", UUID.randomUUID().toString()).put("protocolStatus", "COMPLETE")
                .put("applicationRevision", 0);
        assertThat(pipeline.handle(UUID.randomUUID(), "PackageChecked", checked)).isEmpty();
        checked.put("applicationRevision", 1);
        assertThat(pipeline.handle(UUID.randomUUID(), "PackageChecked", checked)).isPresent();
        mockMvc.perform(put("/api/v1/packages/" + id + "/application").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody(1))).andExpect(status().isOk());
        assertThat(pipeline.handle(UUID.randomUUID(), "PackageChecked", checked)).isEmpty();
        assertThat(dsl.selectFrom(Tables.PACKAGES).where(Tables.PACKAGES.ID.eq(id)).fetchSingle().getProtocolStatus()).isNull();
    }
}
