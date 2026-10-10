package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;

class FileContentIntegrationTest extends AbstractIntegrationTest {

    private static final String SPECIALIST = "SPECIALIST";

    @Autowired
    private DSLContext dsl;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static CurrentUser docproc() {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("service-account").claim("azp", "svc-docproc").build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("SVC_DOCPROC"));
        return new CurrentUser(token, authorities);
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private UUID uploadPdf(byte[] content) throws Exception {
        String created = mockMvc.perform(post("/api/v1/packages").with(authentication(user(SPECIALIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String packageId = JsonPath.read(created, "$.packageId");
        String body = mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "выписка.pdf", "application/pdf", content))
                        .with(authentication(user(SPECIALIST))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.files[0].fileId"));
    }

    @Test
    void specialist_downloadsOriginal_andAccessIsAudited() throws Exception {
        byte[] content = pdf();
        UUID fileId = uploadPdf(content);
        byte[] downloaded = mockMvc.perform(get("/api/v1/files/" + fileId + "/content")
                        .with(authentication(user(SPECIALIST))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(content);
        assertThat(dsl.fetchCount(Tables.FILE_ACCESS_AUDIT, Tables.FILE_ACCESS_AUDIT.FILE_ID.eq(fileId)
                .and(Tables.FILE_ACCESS_AUDIT.ACTION.eq("DOWNLOAD")))).isEqualTo(1);
    }

    @Test
    void auditor_forbidden_unknownFile_404() throws Exception {
        UUID fileId = uploadPdf(pdf());
        mockMvc.perform(get("/api/v1/files/" + fileId + "/content").with(authentication(user("AUDITOR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/files/" + UUID.randomUUID() + "/content")
                        .with(authentication(user(SPECIALIST))))
                .andExpect(status().isNotFound());
    }

    @Test
    void internal_onlyForDocproc() throws Exception {
        byte[] content = pdf();
        UUID fileId = uploadPdf(content);
        byte[] read = mockMvc.perform(get("/internal/package/files/" + fileId).with(authentication(docproc())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-File-Version", "1"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(read).isEqualTo(content);
        mockMvc.perform(get("/internal/package/files/" + fileId).with(authentication(user(SPECIALIST))))
                .andExpect(status().isForbidden());
    }
}
