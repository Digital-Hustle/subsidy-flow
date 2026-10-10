package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;

class DecisionIntegrationTest extends AbstractIntegrationTest {

    private static final String SENIOR = "SENIOR_SPECIALIST";
    private static final String SPECIALIST = "SPECIALIST";

    @Autowired
    private DSLContext dsl;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.getDocumentInformation().setTitle("decision-" + UUID.randomUUID());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String createPackage() throws Exception {
        String created = mockMvc.perform(post("/api/v1/packages").with(authentication(user(SPECIALIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.packageId");
    }

    private void checked(String packageId) {
        dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.STATUS, "CHECKED")
                .set(Tables.PACKAGES.PROTOCOL_STATUS, "COMPLETE").set(Tables.PACKAGES.COMPLETENESS, 100)
                .where(Tables.PACKAGES.ID.eq(UUID.fromString(packageId))).execute();
    }

    private static MockHttpServletRequestBuilder decide(String packageId, String role, String body) {
        return post("/api/v1/packages/" + packageId + "/decision").with(authentication(user(role)))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void approve_onlySeniorAfterProtocol_thenLocked() throws Exception {
        String packageId = createPackage();
        String approve = "{\"decision\":\"APPROVE\"}";
        mockMvc.perform(decide(packageId, SPECIALIST, approve))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(decide(packageId, SENIOR, approve))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROTOCOL_NOT_FOUND"));
        checked(packageId);
        mockMvc.perform(decide(packageId, SENIOR, approve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.decision").value("APPROVE"));
        mockMvc.perform(decide(packageId, SENIOR, approve))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PACKAGE_DECIDED"));
        mockMvc.perform(get("/api/v1/packages/" + packageId).with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.decision").value("APPROVE"))
                .andExpect(jsonPath("$.protocol.completeness").value(100));
        assertThat(dsl.fetchCount(Tables.OUTBOX, Tables.OUTBOX.MESSAGE_KEY.eq(packageId)
                .and(Tables.OUTBOX.PAYLOAD.cast(String.class).contains("PackageDecided")))).isEqualTo(1);
        assertThat(dsl.fetchCount(Tables.OUTBOX, Tables.OUTBOX.MESSAGE_KEY.eq(packageId)
                .and(Tables.OUTBOX.PAYLOAD.cast(String.class).contains("PackageCreated")))).isEqualTo(1);
    }

    @Test
    void return_requiresComment_andReopensUpload() throws Exception {
        String packageId = createPackage();
        checked(packageId);
        mockMvc.perform(decide(packageId, SENIOR, "{\"decision\":\"RETURN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(decide(packageId, SENIOR, "{\"decision\":\"RETURN\",\"comment\":\"Нет сметы\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTING"));
        mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "смета.pdf", "application/pdf", pdf()))
                        .with(authentication(user(SPECIALIST))))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/packages/" + packageId).with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].name").value("смета.pdf"))
                .andExpect(jsonPath("$.files[0].stage").value("RECEIVED"))
                .andExpect(jsonPath("$.decision.comment").value("Нет сметы"));
    }
}
