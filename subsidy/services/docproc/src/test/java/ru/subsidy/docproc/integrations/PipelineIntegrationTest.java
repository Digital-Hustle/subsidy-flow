package ru.subsidy.docproc.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.docproc.config.AbstractIntegrationTest;
import ru.subsidy.docproc.jooq.Tables;
import ru.subsidy.docproc.queue.FileEventsService;
import ru.subsidy.docproc.queue.Job;
import ru.subsidy.docproc.queue.JobQueue;
import ru.subsidy.docproc.pipeline.PipelineRunner;
import ru.subsidy.docproc.text.PackageClient;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;
import ru.subsidy.llm.api.LlmStatus;
import ru.subsidy.llm.api.ModelUnavailableException;

class PipelineIntegrationTest extends AbstractIntegrationTest {

    private static final String PDF = "application/pdf";
    private static final AtomicBoolean MODEL_DOWN = new AtomicBoolean();

    @Autowired
    private FileEventsService events;

    @Autowired
    private JobQueue queue;

    @Autowired
    private PipelineRunner runner;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private PackageClient files;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @BeforeEach
    void reset() throws IOException {
        MODEL_DOWN.set(false);
        when(files.download(any(), anyInt())).thenReturn(pdf(
                "Application for subsidy, INN 7707083893, amount 1 500 000 rub, signed by director",
                "Invoice number 17 from 01.03.2026, seller INN 1234567890, total 500 000.00"));
    }

    private UUID[] received() {
        UUID fileId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("fileId", fileId.toString())
                .put("packageId", packageId.toString()).put("version", 1).put("mime", PDF).put("pages", 2);
        events.handle(UUID.randomUUID(), "FileReceived", payload);
        return new UUID[] {fileId, packageId};
    }

    private Job claimFor(UUID fileId) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Optional<Job> job = queue.claim("test");
            if (job.isEmpty()) {
                break;
            }
            if (job.get().fileId().equals(fileId)) {
                return job.get();
            }
            runner.run(job.get());
        }
        throw new AssertionError("job not claimed " + fileId);
    }

    private String stage(UUID fileId) {
        return queue.byFile(fileId).map(Job::stage).orElseThrow();
    }

    @Test
    void fileReceived_segmentsByContent_parsesFields_andPublishes() throws Exception {
        UUID[] ids = received();
        runner.run(claimFor(ids[0]));

        assertThat(stage(ids[0])).isEqualTo("NEEDS_REVIEW");
        mockMvc.perform(get("/api/v1/packages/" + ids[1] + "/documents").with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("APPLICATION"))
                .andExpect(jsonPath("$.items[0].fromPage").value(1))
                .andExpect(jsonPath("$.items[1].type").value("GUARANTEE_LETTER"))
                .andExpect(jsonPath("$.items[1].reasonCode").value("FIELDS_TO_CHECK"));
        String body = mockMvc.perform(get("/api/v1/packages/" + ids[1] + "/documents")
                        .with(authentication(user("SPECIALIST"))))
                .andReturn().getResponse().getContentAsString();
        JsonNode fields = objectMapper.readTree(body).path("items").path(0).path("fields");
        assertThat(fields.toString()).contains("1500000.00").contains("2026-03-01");

        List<String> types = dsl.select(Tables.OUTBOX.MESSAGE_KEY, Tables.OUTBOX.PAYLOAD).from(Tables.OUTBOX)
                .where(Tables.OUTBOX.MESSAGE_KEY.eq(ids[0].toString())).fetch(row -> row.value2().data());
        assertThat(String.join("\n", types)).contains("DocumentsSegmented").contains("DocumentParsed");
    }

    @Test
    void modelDown_waitsWithoutSpendingAttempt_thenResumes() throws Exception {
        UUID[] ids = received();
        MODEL_DOWN.set(true);
        Job job = claimFor(ids[0]);
        runner.run(job);

        Job waiting = queue.byFile(ids[0]).orElseThrow();
        assertThat(waiting.stage()).isEqualTo("WAITING_MODEL");
        assertThat(waiting.attempts()).isEqualTo(job.attempts());

        MODEL_DOWN.set(false);
        dsl.update(Tables.JOBS).set(Tables.JOBS.NEXT_RUN_AT, Tables.JOBS.CREATED_AT)
                .where(Tables.JOBS.FILE_ID.eq(ids[0])).execute();
        runner.run(claimFor(ids[0]));
        assertThat(stage(ids[0])).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void specialistChangesType_reparsesWithSource() throws Exception {
        UUID[] ids = received();
        runner.run(claimFor(ids[0]));
        String body = mockMvc.perform(get("/api/v1/packages/" + ids[1] + "/documents")
                        .with(authentication(user("SPECIALIST"))))
                .andReturn().getResponse().getContentAsString();
        String documentId = objectMapper.readTree(body).path("items").path(1).path("documentId").asText();

        mockMvc.perform(put("/api/v1/documents/" + documentId + "/type").with(authentication(user("AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BANK_STATEMENT\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/documents/" + documentId + "/type").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BANK_STATEMENT\",\"reason\":\"это справка банка\"}"))
                .andExpect(status().isAccepted());
        runner.run(claimFor(ids[0]));

        mockMvc.perform(get("/api/v1/documents/" + documentId + "/result").with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BANK_STATEMENT"))
                .andExpect(jsonPath("$.source").value("SPECIALIST"));
    }

    @Test
    void concurrentWorkers_neverClaimSameJob() throws Exception {
        for (int index = 0; index < 6; index++) {
            received();
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<UUID> claimed = Collections.synchronizedList(new ArrayList<>());
        for (int index = 0; index < 6; index++) {
            final String worker = "w" + index;
            pool.submit(() -> {
                start.await();
                queue.claim(worker).ifPresent(job -> claimed.add(job.id()));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        Set<UUID> unique = new HashSet<>(claimed);
        assertThat(unique).hasSameSizeAs(claimed);
        assertThat(claimed).isNotEmpty();
    }

    @Test
    void reprocess_onlySenior() throws Exception {
        UUID[] ids = received();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/files/" + ids[0] + "/reprocess").with(authentication(user("SPECIALIST"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/files/" + ids[0] + "/reprocess")
                        .with(authentication(user("SENIOR_SPECIALIST"))))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/doc-types").with(authentication(user("AUDITOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10));
    }

    @TestConfiguration
    static class FakeModel {

        @Bean
        @Primary
        LlmGateway fakeLlm(ObjectMapper json) {
            return new LlmGateway() {
                @Override
                public LlmResult structured(LlmRequest rq) {
                    if (MODEL_DOWN.get()) {
                        throw new ModelUnavailableException("down", null);
                    }
                    ObjectNode root = json.createObjectNode();
                    if ("CLASSIFYING".equals(rq.stage())) {
                        root.putArray("segments")
                                .add(segment(json, 1, 1, "APPLICATION"))
                                .add(segment(json, 2, 2, "GUARANTEE_LETTER"));
                    } else if (rq.schema().toString().contains("guarantorName")) {
                        root.putObject("data").put("guarantorName", "ООО Гарант").put("date", "01.03.2026")
                                .put("inn", "12345").put("amount", "500 000,00");
                        root.putObject("confidence").put("guarantorName", 0.9).put("inn", 0.9).put("amount", 0.9);
                    } else {
                        root.putObject("data").put("inn", "7707083893")
                                .put("requestedAmount", "1 500 000 руб. 00 коп.").put("date", "«1» марта 2026 г.");
                        root.putObject("confidence").put("inn", 0.95).put("requestedAmount", 0.95).put("date", 0.9);
                    }
                    return new LlmResult(root, "fake", "fake-model", 1, null, null, 1);
                }

                @Override
                public LlmStatus status() {
                    return new LlmStatus("fake", "fake-model", null, !MODEL_DOWN.get(), Instant.now(), null);
                }
            };
        }

        private static ObjectNode segment(ObjectMapper json, int first, int last, String type) {
            return json.createObjectNode().put("fromPage", first).put("toPage", last).put("type", type)
                    .put("confidence", 0.95).put("evidence", type);
        }
    }
}
