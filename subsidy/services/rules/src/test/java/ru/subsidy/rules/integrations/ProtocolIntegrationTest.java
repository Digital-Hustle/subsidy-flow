package ru.subsidy.rules.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.config.AbstractIntegrationTest;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.projection.ProjectionService;
import ru.subsidy.rules.protocol.ProtocolService;

class ProtocolIntegrationTest extends AbstractIntegrationTest {

    private static final String INN = "7707083893";
    private static final String SPECIALIST = "SPECIALIST";

    @Autowired
    private ProjectionService projection;

    @Autowired
    private ProtocolService protocols;

    @Autowired
    private DSLContext dsl;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private void event(String type, ObjectNode payload) {
        projection.handle(UUID.randomUUID(), type, payload);
    }

    private ObjectNode field(String name, String value) {
        return objectMapper.createObjectNode().put("name", name).put("value", value).put("confidence", 0.95);
    }

    private UUID[] packageWithApplication() {
        UUID packageId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        event("PackageCreated", objectMapper.createObjectNode().put("packageId", packageId.toString())
                .put("inn", INN).put("programCode", "INNOVATION").put("createdBy", "user-SPECIALIST"));
        event("FileReceived", objectMapper.createObjectNode().put("packageId", packageId.toString())
                .put("fileId", fileId.toString()).put("version", 1));
        ArrayNode fields = objectMapper.createArrayNode()
                .add(field("inn", INN)).add(field("requestedAmount", "1500000.00"));
        ObjectNode parsed = objectMapper.createObjectNode().put("packageId", packageId.toString())
                .put("fileId", fileId.toString()).put("documentId", documentId.toString()).put("version", 1)
                .put("type", "APPLICATION").put("typeConfidence", 0.9).put("source", "MODEL")
                .put("stage", "PARSED").put("fromPage", 1).put("toPage", 1);
        parsed.set("fields", fields);
        event("DocumentParsed", parsed);
        event("StageChanged", objectMapper.createObjectNode().put("packageId", packageId.toString())
                .put("fileId", fileId.toString()).put("version", 1).put("stage", "PARSED"));
        return new UUID[] {packageId, documentId};
    }

    @Test
    void protocol_beforeCheck_409_thenIncompleteWithMissing() throws Exception {
        UUID[] ids = packageWithApplication();
        String url = "/api/v1/packages/" + ids[0] + "/protocol";
        mockMvc.perform(get(url).with(authentication(user(SPECIALIST))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CHECKED_YET"));

        Optional<UUID> created = protocols.check(ids[0]);
        assertThat(created).isPresent();
        assertThat(protocols.check(ids[0])).isEmpty();

        mockMvc.perform(get(url).with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.programCode").value("INNOVATION"));
        long outbox = dsl.fetchCount(Tables.OUTBOX, Tables.OUTBOX.MESSAGE_KEY.eq(ids[0].toString()));
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    void fieldOverride_createsNewVersion_auditorForbidden() throws Exception {
        UUID[] ids = packageWithApplication();
        protocols.check(ids[0]);
        String body = "{\"documentId\":\"" + ids[1] + "\",\"field\":\"requestedAmount\","
                + "\"value\":\"1000000.00\",\"reason\":\"сверено с оригиналом\"}";
        String url = "/api/v1/packages/" + ids[0] + "/fields";
        mockMvc.perform(put(url).with(authentication(user("AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(url).with(authentication(user(SPECIALIST)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        assertThat(protocols.check(ids[0])).isPresent();

        mockMvc.perform(get("/api/v1/packages/" + ids[0] + "/protocols").with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        mockMvc.perform(get("/api/v1/packages/" + ids[0] + "/protocols/1").with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/v1/organizations/" + INN + "/history").with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").isNotEmpty());
    }

    @Test
    void export_allFormats() throws Exception {
        UUID[] ids = packageWithApplication();
        protocols.check(ids[0]);
        for (String format : List.of("docx", "xlsx", "pdf")) {
            byte[] content = mockMvc.perform(get("/api/v1/packages/" + ids[0] + "/protocol/export")
                            .param("format", format).with(authentication(user(SPECIALIST))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(content.length).as(format).isGreaterThan(100);
        }
    }

    @Test
    void insights_andPrograms() throws Exception {
        UUID[] ids = packageWithApplication();
        protocols.check(ids[0]);
        mockMvc.perform(get("/api/v1/stats/errors").with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/expiring").param("days", "30").with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/programs").with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[1].code").value("INNOVATION"));
        String program = mockMvc.perform(get("/api/v1/programs/INNOVATION").with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode current = (ObjectNode) objectMapper.readTree(program);
        ObjectNode update = objectMapper.createObjectNode().put("name", "Инновационный проект (изм.)");
        update.set("params", current.path("params"));
        update.set("checklist", current.path("checklist"));
        mockMvc.perform(put("/api/v1/programs/INNOVATION").with(authentication(user(SPECIALIST)))
                        .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/programs/INNOVATION").with(authentication(user("SENIOR_SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }
}
