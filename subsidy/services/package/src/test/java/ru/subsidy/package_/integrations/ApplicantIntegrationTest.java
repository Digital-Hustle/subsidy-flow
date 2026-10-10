package ru.subsidy.package_.integrations;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;

class ApplicantIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/applicant";
    private static final String APPLICATION = """
            {"orgInn":"7707083893","orgName":"ООО Заявитель","ogrn":"1027700132195",
             "address":{"value":"Ростов-на-Дону, ул. Тестовая, 1","purpose":"PROJECT"},
             "project":"Пляж","requestedAmount":"150000.00","applicationDate":"2026-10-10",
             "signatory":{"fullName":"Иванов Иван","position":"Директор","authority":"Устав"}}
            """;
    private static final String PROGRAMS = """
            [{"code":"BEACH","version":2,"name":"Обустройство пляжа","accepting":true,
              "params":{"description":"Субсидия на пляж","deadline":"2026-12-31"},
              "checklist":[{"item":"APPLICATION","kind":"REQUIRED","title":"Заявление","types":["APPLICATION"]},
                           {"item":"GIMS","kind":"CONDITIONAL","title":"ГИМС","types":["GIMS_DECLARATION"],
                            "condition":"needsGims","conditionText":"если нужен ГИМС"}]},
             {"code":"CLOSED","version":1,"name":"Закрытая","accepting":false,"params":{},"checklist":[]}]
            """;

    @BeforeEach
    void programs() throws Exception {
        JsonNode items = objectMapper.readTree(PROGRAMS);
        when(programCatalog.all()).thenReturn(List.of(items.get(0), items.get(1)));
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("заявление");
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static CurrentUser specialist() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("specialist").build();
        return new CurrentUser(jwt, List.of(new SimpleGrantedAuthority("ROLE_SPECIALIST")));
    }

    private String create() throws Exception {
        return mockMvc.perform(post(BASE + "/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentPd\":true,\"programCode\":\"BEACH\","
                                + "\"contact\":{\"email\":\"Ivanov@Example.ru\",\"phone\":\"+7 900 000-00-01\"}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resumeCode").isString())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void programs_onlyOpen() throws Exception {
        mockMvc.perform(get(BASE + "/programs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("BEACH"))
                .andExpect(jsonPath("$.items[0].description").value("Субсидия на пляж"));
    }

    @Test
    void create_requiresConsentAndOpenProgram() throws Exception {
        mockMvc.perform(post(BASE + "/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentPd\":false,\"programCode\":\"BEACH\",\"contact\":{\"email\":\"a@b.ru\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mockMvc.perform(post(BASE + "/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentPd\":true,\"programCode\":\"CLOSED\",\"contact\":{\"email\":\"a@b.ru\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROGRAM_UNKNOWN"));
    }

    @Test
    void fullFlow_draftHiddenUntilSubmitted() throws Exception {
        String created = create();
        String id = JsonPath.read(created, "$.applicationId");
        String key = "Applicant " + JsonPath.read(created, "$.accessKey");
        String app = BASE + "/applications/" + id;

        mockMvc.perform(get(app).header(HttpHeaders.AUTHORIZATION, "Applicant wrong"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
        mockMvc.perform(get(app).header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.programName").value("Обустройство пляжа"))
                .andExpect(jsonPath("$.missing[0]").value("APPLICATION"))
                .andExpect(jsonPath("$.requiredDocuments[1].required").value(false));
        mockMvc.perform(post(app + "/submit").header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DOCUMENTS_MISSING"))
                .andExpect(jsonPath("$.missingFields[0]").value("application"));

        mockMvc.perform(put(app + "/application").header(HttpHeaders.AUTHORIZATION, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"application\":" + APPLICATION + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationRevision").value(1));
        mockMvc.perform(multipart(app + "/documents").file(new MockMultipartFile("file", "заявление.pdf",
                                "application/pdf", pdf())).param("slotType", "APPLICATION")
                        .header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.stage").value("RECEIVED"));
        mockMvc.perform(multipart(app + "/documents").file(new MockMultipartFile("file", "фото.png",
                                "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}))
                        .header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.hint").isString());
        mockMvc.perform(get("/api/v1/packages").with(authentication(specialist())))
                .andExpect(jsonPath("$.items[*].id", Matchers.not(Matchers.hasItem(id))));

        mockMvc.perform(get(app).header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].slotType").value("APPLICATION"))
                .andExpect(jsonPath("$.missing.length()").value(0));
        mockMvc.perform(post(app + "/submit").header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(get("/api/v1/packages").with(authentication(specialist())))
                .andExpect(jsonPath("$.items[*].id", Matchers.hasItem(id)));
        mockMvc.perform(put(app + "/application").header(HttpHeaders.AUTHORIZATION, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1,\"application\":" + APPLICATION + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PACKAGE_LOCKED"));
        mockMvc.perform(delete(app).header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isConflict());
    }

    @Test
    void resume_rotatesKey_andDeleteRevokes() throws Exception {
        String created = create();
        String id = JsonPath.read(created, "$.applicationId");
        String oldKey = "Applicant " + JsonPath.read(created, "$.accessKey");
        String code = JsonPath.read(created, "$.resumeCode");
        mockMvc.perform(post(BASE + "/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneOrEmail\":\"ivanov@example.ru\",\"resumeCode\":\"WRONG123\"}"))
                .andExpect(status().isNotFound());
        String resumed = mockMvc.perform(post(BASE + "/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneOrEmail\":\"8 900 000-00-01\",\"resumeCode\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationId").value(id))
                .andReturn().getResponse().getContentAsString();
        String key = "Applicant " + JsonPath.read(resumed, "$.accessKey");
        String app = BASE + "/applications/" + id;
        mockMvc.perform(get(app).header(HttpHeaders.AUTHORIZATION, oldKey)).andExpect(status().isNotFound());
        mockMvc.perform(get(app + "/stream/ticket").header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ticket").isString());
        mockMvc.perform(delete(app).header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isNoContent());
        mockMvc.perform(get(app).header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isNotFound());
    }
}
