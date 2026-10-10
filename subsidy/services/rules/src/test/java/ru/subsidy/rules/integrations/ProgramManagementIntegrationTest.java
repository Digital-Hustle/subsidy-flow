package ru.subsidy.rules.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.config.AbstractIntegrationTest;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.projection.ProjectionService;
import ru.subsidy.rules.protocol.ProtocolQueryService;
import ru.subsidy.rules.protocol.ProtocolService;

class ProgramManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DSLContext dsl;
    @Autowired
    private ProjectionService projection;
    @Autowired
    private ProtocolService protocols;
    @Autowired
    private ProtocolQueryService query;

    private static CurrentUser principal(String authority) {
        return new CurrentUser(Jwt.withTokenValue("test").header("alg", "none").subject("test-user").build(),
                List.of(new SimpleGrantedAuthority(authority)));
    }

    private ObjectNode settings(String max, boolean requireConsent) {
        ObjectNode body = objectMapper.createObjectNode().put("name", "Новая программа");
        var spec = body.putObject("params").putArray("rules").addObject().put("code", "MAX_REQUEST")
                .put("op", "RANGE").put("max", max);
        spec.putArray("value").add("APPLICANT.requestedAmount");
        var checklist = body.putArray("checklist");
        if (requireConsent) {
            checklist.addObject().put("item", "CONSENT").put("title", "Согласие").put("kind", "REQUIRED")
                    .putArray("types").add("PD_CONSENT");
        }
        return body;
    }

    private void event(String type, ObjectNode payload) {
        projection.handle(UUID.randomUUID(), type, payload);
    }

    @Test
    void apiProgramAndChecklistChangesRecheckExistingPackages_andOldProtocolIsImmutable() throws Exception {
        String code = "B3_" + UUID.randomUUID().toString().replace("-", "");
        try {
            mockMvc.perform(put("/api/v1/programs/" + code).with(authentication(principal("ROLE_SENIOR_SPECIALIST")))
                            .contentType(MediaType.APPLICATION_JSON).content(settings("100.00", false).toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
            mockMvc.perform(get("/internal/rules/programs/" + code).with(authentication(principal("SVC_PACKAGE"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
            UUID pkg = UUID.randomUUID();
            final UUID file = UUID.randomUUID();
            event("PackageCreated", objectMapper.createObjectNode().put("packageId", pkg.toString())
                    .put("inn", "7707083893").put("programCode", code));
            ObjectNode changed = objectMapper.createObjectNode().put("packageId", pkg.toString())
                    .put("orgId", UUID.randomUUID().toString()).put("inn", "7707083893").put("applicationRevision", 1);
            changed.putObject("application").put("requestedAmount", "150.00");
            event("PackageApplicationChanged", changed);
            ObjectNode received = objectMapper.createObjectNode().put("packageId", pkg.toString())
                    .put("fileId", file.toString()).put("version", 1);
            received.putObject("electronicSignature").put("status", "PRESENT");
            event("FileReceived", received);
            event("DocumentParsed", objectMapper.createObjectNode().put("packageId", pkg.toString())
                    .put("fileId", file.toString()).put("documentId", UUID.randomUUID().toString())
                    .put("version", 1).put("type", "APPLICATION").put("stage", "PARSED"));
            event("StageChanged", received.deepCopy().put("stage", "PARSED"));
            protocols.check(pkg);
            assertThat(query.findLatest(pkg).orElseThrow().results()).anyMatch(result -> "MAX_REQUEST".equals(result.rule())
                    && "FAILED".equals(result.status()));
            mockMvc.perform(put("/api/v1/programs/" + code).with(authentication(principal("ROLE_SENIOR_SPECIALIST")))
                            .contentType(MediaType.APPLICATION_JSON).content(settings("200.00", true).toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
            assertThat(dsl.fetchExists(Tables.RECHECK_REQUESTS, Tables.RECHECK_REQUESTS.PACKAGE_ID.eq(pkg))).isTrue();
            assertThat(protocols.check(pkg)).isPresent();
            var latest = query.findLatest(pkg).orElseThrow();
            assertThat(latest.programVersion()).isEqualTo(2);
            assertThat(latest.results()).anyMatch(result -> "MAX_REQUEST".equals(result.rule()) && "PASSED".equals(result.status()));
            assertThat(latest.completeness().path("missing").get(0).path("item").asText()).isEqualTo("CONSENT");
            assertThat(dsl.select(Tables.PROTOCOLS.FACTS).from(Tables.PROTOCOLS).where(Tables.PROTOCOLS.PACKAGE_ID.eq(pkg))
                    .and(Tables.PROTOCOLS.VERSION.eq(1)).fetchSingle().value1().data()).contains(code + "@1");
        } finally {
            dsl.deleteFrom(Tables.PROGRAMS).where(Tables.PROGRAMS.CODE.eq(code)).execute();
        }
    }

    @Test
    void internalCatalogueIsOnlyForPackage_andUnknownProgramIs404() throws Exception {
        mockMvc.perform(get("/internal/rules/programs/INNOVATION")).andExpect(status().isUnauthorized());
        for (String role : List.of("ROLE_SPECIALIST", "ROLE_SENIOR_SPECIALIST", "SVC_ASSISTANT")) {
            mockMvc.perform(get("/internal/rules/programs/INNOVATION").with(authentication(principal(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/internal/rules/programs/UNKNOWN").with(authentication(principal("SVC_PACKAGE"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROGRAM_NOT_FOUND"));
        mockMvc.perform(get("/internal/rules/packages/" + UUID.randomUUID() + "/facts")
                        .with(authentication(principal("SVC_PACKAGE")))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/programs/BAD").with(authentication(principal("ROLE_SENIOR_SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"rules\":[{\"code\":\"BAD\",\"op\":\"MAGIC\"}]},\"checklist\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
