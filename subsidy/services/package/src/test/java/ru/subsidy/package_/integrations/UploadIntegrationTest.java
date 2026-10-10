package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import ru.subsidy.package_.storage.FileCrypto;
import ru.subsidy.package_.storage.FileCrypto.Sealed;

class UploadIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET_NAME = "Секретный_Иванов.pdf";

    @Autowired
    private DSLContext dsl;

    @Autowired
    private FileCrypto crypto;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static byte[] pdf() throws IOException {
        return pdf("doc");
    }

    private static byte[] pdf(String title) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle(title);
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String createPackage() throws Exception {
        String body = mockMvc.perform(post("/api/v1/packages").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.packageId");
    }

    @Test
    void upload_acceptsGood_rejectsEmpty_encryptsAndWritesOutboxWithoutName() throws Exception {
        String packageId = createPackage();
        byte[] content = pdf();
        String body = mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", SECRET_NAME, "application/pdf", content))
                        .file(new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]))
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].stage").value("RECEIVED"))
                .andExpect(jsonPath("$.rejected[0].code").value("FILE_EMPTY"))
                .andExpect(jsonPath("$.rejected[0].hint").exists())
                .andReturn().getResponse().getContentAsString();
        UUID fileId = UUID.fromString(JsonPath.read(body, "$.files[0].fileId"));

        var version = dsl.selectFrom(Tables.FILE_VERSIONS).where(Tables.FILE_VERSIONS.FILE_ID.eq(fileId)).fetchSingle();
        assertThat(version.getContentEnc()).isNotEqualTo(content);
        assertThat(crypto.decrypt(new Sealed(version.getContentEnc(), version.getNonce()),
                FileCrypto.aad(fileId, 1))).isEqualTo(content);

        String payload = dsl.select(Tables.OUTBOX.PAYLOAD).from(Tables.OUTBOX)
                .where(Tables.OUTBOX.MESSAGE_KEY.eq(fileId.toString())).fetchSingle().value1().data();
        assertThat(payload).contains("FileReceived", packageId).doesNotContain("Иванов", ".pdf");
    }

    @Test
    void upload_byAuditor_forbidden_unknownPackage_404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "a.pdf", "application/pdf", pdf());
        mockMvc.perform(multipart("/api/v1/packages/" + UUID.randomUUID() + "/files").file(file)
                        .with(authentication(user("AUDITOR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/packages/" + UUID.randomUUID() + "/files").file(file)
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_zip_isExpandedIntoFiles() throws Exception {
        String packageId = createPackage();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : List.of("a.pdf", "b.pdf")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(pdf(name));
                zip.closeEntry();
            }
        }
        mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "pack.zip", "application/zip", out.toByteArray()))
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.rejected.length()").value(0));
    }

    @Test
    void upload_sameFileTwice_secondRejectedAsDuplicate() throws Exception {
        String packageId = createPackage();
        byte[] content = pdf();
        mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "one.pdf", "application/pdf", content))
                        .file(new MockMultipartFile("files", "copy.pdf", "application/pdf", content))
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.rejected[0].code").value("DUPLICATE_FILE"))
                .andExpect(jsonPath("$.rejected[0].name").value("copy.pdf"));
    }
}
