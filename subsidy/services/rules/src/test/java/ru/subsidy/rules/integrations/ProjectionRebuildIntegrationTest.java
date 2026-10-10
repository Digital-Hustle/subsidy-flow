package ru.subsidy.rules.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.config.AbstractIntegrationTest;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.projection.ProjectionRebuild;
import ru.subsidy.rules.projection.ProjectionService;

class ProjectionRebuildIntegrationTest extends AbstractIntegrationTest {

    private static final String PACKAGE_ID = "packageId";
    private static final String FILE_ID = "fileId";
    private static final String VERSION = "version";

    @Autowired
    private ProjectionService projection;

    @Autowired
    private ProjectionRebuild rebuild;

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

    /** Пакет, где файл разобран (StageChanged PARSED), а DocumentParsed потерялся. */
    private UUID[] parsedFileWithoutDocuments() {
        UUID packageId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        event("PackageCreated", objectMapper.createObjectNode().put(PACKAGE_ID, packageId.toString())
                .put("inn", "7707083893").put("programCode", "INNOVATION").put("createdBy", "user-SPECIALIST"));
        event("FileReceived", objectMapper.createObjectNode().put(PACKAGE_ID, packageId.toString())
                .put(FILE_ID, fileId.toString()).put(VERSION, 1));
        event("StageChanged", objectMapper.createObjectNode().put(PACKAGE_ID, packageId.toString())
                .put(FILE_ID, fileId.toString()).put(VERSION, 1).put("stage", "PARSED"));
        return new UUID[] {packageId, fileId};
    }

    private JsonNode docprocDocument(UUID fileId, UUID documentId, int resultVersion, String inn) {
        ObjectNode doc = objectMapper.createObjectNode().put("documentId", documentId.toString())
                .put(FILE_ID, fileId.toString()).put("fileVersion", 1).put("fromPage", 1).put("toPage", 2)
                .put("stage", "PARSED").put("type", "APPLICATION").put("typeConfidence", 0.93)
                .put("source", "MODEL").put("resultVersion", resultVersion);
        doc.set("fields", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("name", "inn").put("value", inn).put("confidence", 0.9)));
        return doc;
    }

    @Test
    void lostDocumentParsed_reconcileRestoresProjection() {
        UUID[] ids = parsedFileWithoutDocuments();
        UUID documentId = UUID.randomUUID();
        assertThat(rebuild.incomplete(ids[0])).isTrue();

        ProjectionService.Reconciled first = projection.reconcile(ids[0],
                List.of(docprocDocument(ids[1], documentId, 1, "7707083893")));

        assertThat(first.updated()).isEqualTo(1);
        assertThat(first.removed()).isZero();
        assertThat(rebuild.incomplete(ids[0])).isFalse();
        var row = dsl.selectFrom(Tables.PACKAGE_DOCUMENTS)
                .where(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.eq(documentId)).fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.getType()).isEqualTo("APPLICATION");
        assertThat(row.getFileVersion()).isEqualTo(1);
        assertThat(row.getToPage()).isEqualTo(2);
        assertThat(row.getFields().data()).contains("7707083893");
    }

    @Test
    void sameVersionSkipped_newerApplied_missingRemoved() {
        UUID[] ids = parsedFileWithoutDocuments();
        UUID kept = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        projection.reconcile(ids[0], List.of(docprocDocument(ids[1], kept, 1, "7707083893"),
                docprocDocument(ids[1], gone, 1, "7707083893")));

        ProjectionService.Reconciled same = projection.reconcile(ids[0],
                List.of(docprocDocument(ids[1], kept, 1, "0000000000")));
        assertThat(same.updated()).isZero();
        assertThat(same.removed()).isEqualTo(1);

        ProjectionService.Reconciled newer = projection.reconcile(ids[0],
                List.of(docprocDocument(ids[1], kept, 2, "7728168971")));
        assertThat(newer.updated()).isEqualTo(1);
        assertThat(dsl.select(Tables.PACKAGE_DOCUMENTS.FIELDS).from(Tables.PACKAGE_DOCUMENTS)
                .where(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.eq(kept)).fetchOne().value1().data())
                .contains("7728168971");
        assertThat(dsl.select(Tables.PACKAGE_DOCUMENTS.REMOVED).from(Tables.PACKAGE_DOCUMENTS)
                .where(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID.eq(gone)).fetchOne().value1()).isTrue();
    }

    @Test
    void endpoint_seniorOnly_docprocUnavailable503_unknown404() throws Exception {
        UUID[] ids = parsedFileWithoutDocuments();
        String url = "/api/v1/packages/" + ids[0] + "/projection/rebuild";
        mockMvc.perform(post(url).with(authentication(user("SPECIALIST")))).andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication(user("SENIOR_SPECIALIST"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        mockMvc.perform(post("/api/v1/packages/" + UUID.randomUUID() + "/projection/rebuild")
                        .with(authentication(user("SENIOR_SPECIALIST"))))
                .andExpect(status().isNotFound());
        assertThat(rebuild.rebuildIfIncomplete(ids[0])).isFalse();
    }
}
