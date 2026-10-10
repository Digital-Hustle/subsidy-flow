package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;

class PreviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DSLContext dsl;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("preview-" + UUID.randomUUID());
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void page_isRenderedAndCached() throws Exception {
        String created = mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String packageId = JsonPath.read(created, "$.packageId");
        String body = mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "договор.pdf", "application/pdf", pdf()))
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(body, "$.files[0].fileId");
        String url = "/api/v1/files/" + fileId + "/pages/2.png";
        byte[] first = mockMvc.perform(get(url).with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(first[1]).isEqualTo((byte) 'P');
        mockMvc.perform(get(url).with(authentication(user("SPECIALIST")))).andExpect(status().isOk());
        assertThat(dsl.fetchCount(Tables.PAGE_PREVIEWS, Tables.PAGE_PREVIEWS.FILE_ID.eq(UUID.fromString(fileId))))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/files/" + fileId + "/pages/3.png").with(authentication(user("SPECIALIST"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/" + UUID.randomUUID() + "/pages/1.png")
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isNotFound());
    }
}
