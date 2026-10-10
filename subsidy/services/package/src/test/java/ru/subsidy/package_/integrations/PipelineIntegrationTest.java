package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.pipeline.PipelineEventsService;
import ru.subsidy.package_.stream.StreamHub;

class PipelineIntegrationTest extends AbstractIntegrationTest {

    private static final String SPECIALIST = "SPECIALIST";

    @Autowired
    private DSLContext dsl;
    @Autowired
    private PipelineEventsService service;
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private StreamHub hub;

    private static CurrentUser user() {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + SPECIALIST).claim("roles", List.of(SPECIALIST)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + SPECIALIST)));
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private UUID[] upload() throws Exception {
        String created = mockMvc.perform(post("/api/v1/packages").with(authentication(user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgInn\":\"7707083893\",\"programCode\":\"INNOVATION\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String packageId = JsonPath.read(created, "$.packageId");
        String body = mockMvc.perform(multipart("/api/v1/packages/" + packageId + "/files")
                        .file(new MockMultipartFile("files", "смета.pdf", "application/pdf", pdf()))
                        .with(authentication(user())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return new UUID[] {UUID.fromString(packageId), UUID.fromString(JsonPath.read(body, "$.files[0].fileId"))};
    }

    private JsonNode node(Map<String, Object> payload) {
        return objectMapper.valueToTree(payload);
    }

    private String packageStatus(UUID packageId) {
        return dsl.select(Tables.PACKAGES.STATUS).from(Tables.PACKAGES).where(Tables.PACKAGES.ID.eq(packageId))
                .fetchOne(Tables.PACKAGES.STATUS);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("ожидание условия").isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    @Test
    void stageParsedChecked_updatePackage_idempotently() throws Exception {
        UUID[] ids = upload();
        UUID packageId = ids[0];
        UUID fileId = ids[1];
        UUID eventId = UUID.randomUUID();
        JsonNode stage = node(Map.of("fileId", fileId.toString(), "stage", "OCR"));
        assertThat(service.handle(eventId, "StageChanged", stage)).isPresent();
        assertThat(service.handle(eventId, "StageChanged", stage)).isEmpty();
        assertThat(packageStatus(packageId)).isEqualTo("PROCESSING");

        UUID documentId = UUID.randomUUID();
        assertThat(service.handle(UUID.randomUUID(), "DocumentParsed", node(Map.of("fileId", fileId.toString(),
                "documentId", documentId.toString(), "type", "COST_JUSTIFICATION", "typeConfidence", 0.93,
                "fromPage", 1, "toPage", 1, "source", "TEXT_LAYER", "stage", "NEEDS_REVIEW")))).isPresent();
        var doc = dsl.selectFrom(Tables.FILE_DOCUMENTS)
                .where(Tables.FILE_DOCUMENTS.DOCUMENT_ID.eq(documentId)).fetchOne();
        assertThat(doc.getDocType()).isEqualTo("COST_JUSTIFICATION");
        assertThat(doc.getSource()).isEqualTo("TEXT_LAYER");
        assertThat(doc.getStage()).isEqualTo("NEEDS_REVIEW");

        assertThat(service.handle(UUID.randomUUID(), "PackageChecked", node(Map.of("packageId", packageId.toString(),
                "protocolStatus", "WARN", "completeness", 80, "failed", 0, "warnings", 2, "needsReview", 1))))
                .isPresent();
        assertThat(packageStatus(packageId)).isEqualTo("CHECKED");
    }

    @Test
    void outboxRelayed_andKafkaEventConsumed() throws Exception {
        UUID[] ids = upload();
        UUID fileId = ids[1];
        await(() -> dsl.fetchCount(Tables.OUTBOX, Tables.OUTBOX.PUBLISHED_AT.isNull()) == 0);
        String envelope = objectMapper.writeValueAsString(Map.of("eventId", UUID.randomUUID().toString(),
                "type", "StageChanged", "aggregateId", fileId.toString(),
                "payload", Map.of("fileId", fileId.toString(), "stage", "CLASSIFY")));
        kafka.send(Topics.DOCPROC_DOCUMENT_V1, fileId.toString(), envelope).get();
        await(() -> "CLASSIFY".equals(dsl.select(Tables.FILES.STAGE).from(Tables.FILES)
                .where(Tables.FILES.ID.eq(fileId)).fetchOne(Tables.FILES.STAGE)));
    }

    @Test
    void stream_requiresTicket() throws Exception {
        UUID packageId = upload()[0];
        mockMvc.perform(get("/api/v1/stream").param("packageId", packageId.toString()).param("ticket", "bad"))
                .andExpect(status().isForbidden());
        String body = mockMvc.perform(get("/api/v1/stream/ticket").with(authentication(user())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ticket = JsonPath.read(body, "$.ticket");
        mockMvc.perform(get("/api/v1/stream").param("packageId", packageId.toString()).param("ticket", ticket))
                .andExpect(request().asyncStarted());
        assertThat(hub.subscribers(packageId)).isEqualTo(1);
    }
}
