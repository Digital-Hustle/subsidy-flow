package ru.subsidy.rules.facts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.jooq.tables.records.PackageViewRecord;

/**
 * Собирает факты пакета из проекции: документы, поля (правка специалиста ?? значение модели), файлы.
 */
@Component
public class FactsAssembler {

    private static final String CONFIDENCE = "confidence";
    private static final String FAILED = "FAILED";

    private final DSLContext dsl;
    private final ObjectMapper json;
    private final RulesProperties props;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param json Jackson
     * @param props настройки
     */
    public FactsAssembler(DSLContext dsl, ObjectMapper json, RulesProperties props) {
        this.dsl = dsl;
        this.json = json;
        this.props = props;
    }

    /**
     * Факты пакета.
     *
     * @param packageId пакет
     * @return факты или пусто, если пакет ещё не известен rules
     */
    public Optional<PackageFacts> assemble(UUID packageId) {
        PackageViewRecord view = dsl.selectFrom(Tables.PACKAGE_VIEW).where(Tables.PACKAGE_VIEW.PACKAGE_ID.eq(packageId))
                .fetchOne();
        if (view == null) {
            return Optional.empty();
        }
        Map<UUID, Map<String, String>> overrides = overrides(packageId);
        List<DocFacts> documents = dsl.selectFrom(Tables.PACKAGE_DOCUMENTS)
                .where(Tables.PACKAGE_DOCUMENTS.PACKAGE_ID.eq(packageId))
                .and(Tables.PACKAGE_DOCUMENTS.REMOVED.isFalse())
                .orderBy(Tables.PACKAGE_DOCUMENTS.FILE_ID, Tables.PACKAGE_DOCUMENTS.FROM_PAGE)
                .fetch(rec -> document(rec, overrides.getOrDefault(rec.get(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID),
                        Map.of())));
        List<FileFacts> files = dsl.selectFrom(Tables.PACKAGE_FILES)
                .where(Tables.PACKAGE_FILES.PACKAGE_ID.eq(packageId)).and(Tables.PACKAGE_FILES.REMOVED.isFalse())
                .orderBy(Tables.PACKAGE_FILES.FILE_ID)
                .fetch(rec -> new FileFacts(rec.getFileId(), rec.getVersion(), rec.getStage(), rec.getReasonCode()));
        LocalDate packageDate = view.getCreatedAt().atZoneSameInstant(props.zone()).toLocalDate();
        return Optional.of(new PackageFacts(packageId, view.getInn(), view.getProgramCode(), packageDate, documents,
                files));
    }

    private Map<UUID, Map<String, String>> overrides(UUID packageId) {
        Map<UUID, Map<String, String>> result = new HashMap<>();
        dsl.selectFrom(Tables.FIELD_OVERRIDES).where(Tables.FIELD_OVERRIDES.PACKAGE_ID.eq(packageId))
                .orderBy(Tables.FIELD_OVERRIDES.CREATED_AT)
                .forEach(rec -> result.computeIfAbsent(rec.getDocumentId(), key -> new HashMap<>())
                        .put(rec.getField(), rec.getValue()));
        return result;
    }

    private DocFacts document(Record rec, Map<String, String> edits) {
        Map<String, FieldFact> fields = new LinkedHashMap<>();
        for (JsonNode node : read(rec.get(Tables.PACKAGE_DOCUMENTS.FIELDS).data())) {
            String name = node.path("name").asText();
            boolean flagged = false;
            for (JsonNode check : node.path("checks")) {
                flagged |= FAILED.equals(check.path("status").asText());
            }
            BigDecimal confidence = node.hasNonNull(CONFIDENCE) ? node.path(CONFIDENCE).decimalValue() : null;
            fields.put(name, new FieldFact(name, node.get("value"), confidence, FieldFact.MODEL, flagged));
        }
        edits.forEach((name, value) -> fields.put(name, new FieldFact(name,
                value == null ? null : TextNode.valueOf(value), BigDecimal.ONE, FieldFact.SPECIALIST, false)));
        return new DocFacts(rec.get(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID), rec.get(Tables.PACKAGE_DOCUMENTS.FILE_ID),
                rec.get(Tables.PACKAGE_DOCUMENTS.TYPE), rec.get(Tables.PACKAGE_DOCUMENTS.TYPE_CONFIDENCE),
                rec.get(Tables.PACKAGE_DOCUMENTS.STAGE), rec.get(Tables.PACKAGE_DOCUMENTS.REASON_CODE),
                rec.get(Tables.PACKAGE_DOCUMENTS.FROM_PAGE), rec.get(Tables.PACKAGE_DOCUMENTS.TO_PAGE), fields);
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("package_documents.fields: битый JSON", broken);
        }
    }
}
