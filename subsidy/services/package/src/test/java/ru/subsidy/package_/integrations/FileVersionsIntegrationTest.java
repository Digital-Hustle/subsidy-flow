package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;

class FileVersionsIntegrationTest extends AbstractIntegrationTest {

    private static final String SPECIALIST = "SPECIALIST";

    @Autowired
    private DSLContext dsl;

    private static CurrentUser user() {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + SPECIALIST).claim("roles", List.of(SPECIALIST)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + SPECIALIST)));
    }

    private static byte[] pdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int idx = 0; idx < pages; idx++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String[] upload(byte[] content) throws Exception {
        String created = mockMvc.perform(post("/api/v1/packages").with(authentication(user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String packageId = JsonPath.read(created, "$.packageId");
        String body = mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "счёт.pdf", "application/pdf", content))
                        .with(authentication(user())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return new String[] {packageId, JsonPath.read(body, "$.files[0].fileId")};
    }

    @Test
    void replace_createsVersion2_andRejectsSameContent() throws Exception {
        byte[] first = pdf(1);
        String[] ids = upload(first);
        String url = "/api/v1/packages/" + ids[0] + "/files/" + ids[1] + "/versions";
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "счёт-2.pdf", "application/pdf", first))
                        .with(authentication(user())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rejected[0].code").value("DUPLICATE_FILE"));
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "счёт-2.pdf", "application/pdf", pdf(2)))
                        .with(authentication(user())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.files[0].fileId").value(ids[1]));
        UUID fileId = UUID.fromString(ids[1]);
        assertThat(dsl.select(Tables.FILES.CURRENT_VERSION).from(Tables.FILES).where(Tables.FILES.ID.eq(fileId))
                .fetchOne(Tables.FILES.CURRENT_VERSION)).isEqualTo(2);
        assertThat(dsl.fetchCount(Tables.OUTBOX, Tables.OUTBOX.MESSAGE_KEY.eq(ids[1]))).isEqualTo(2);
        mockMvc.perform(get("/api/v1/files/" + ids[1] + "/content").param("version", "1")
                        .with(authentication(user())))
                .andExpect(status().isOk());
    }

    @Test
    void remove_hidesFile() throws Exception {
        String[] ids = upload(pdf(1));
        mockMvc.perform(delete("/api/v1/packages/" + ids[0] + "/files/" + ids[1]).with(authentication(user())))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/packages/" + ids[0] + "/files/" + ids[1]).with(authentication(user())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/" + ids[1] + "/content").with(authentication(user())))
                .andExpect(status().isNotFound());
    }
}
