package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.FieldFact;
import ru.subsidy.rules.facts.FileFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

final class Fixture {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String APPLICANT = "7707083893";
    static final LocalDate PACKAGE_DATE = LocalDate.of(2026, 3, 20);

    private final List<DocFacts> docs = new ArrayList<>();

    private Fixture() {
    }

    static Fixture pkg() {
        return new Fixture();
    }

    Fixture doc(String type, Object... pairs) {
        Map<String, FieldFact> fields = new LinkedHashMap<>();
        for (int idx = 0; idx < pairs.length; idx += 2) {
            String name = (String) pairs[idx];
            JsonNode value = JSON.valueToTree(pairs[idx + 1]);
            fields.put(name, new FieldFact(name, value, BigDecimal.ONE, FieldFact.MODEL, false));
        }
        docs.add(new DocFacts(UUID.randomUUID(), UUID.randomUUID(), type, BigDecimal.ONE, "PARSED", null,
                docs.size() + 1, docs.size() + 1, fields));
        return this;
    }

    PackageFacts facts() {
        return new PackageFacts(UUID.randomUUID(), APPLICANT, "INNOVATION", PACKAGE_DATE, docs,
                List.of(new FileFacts(UUID.randomUUID(), 1, "PARSED", null)));
    }

    static ProgramParams params() {
        return new ProgramParams(JSON.createObjectNode());
    }

    static List<Map<String, Object>> items(Object... rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int idx = 0; idx < rows.length; idx += 3) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", "Позиция " + (idx / 3 + 1));
            row.put("qty", rows[idx]);
            row.put("price", rows[idx + 1]);
            row.put("amount", rows[idx + 2]);
            out.add(row);
        }
        return out;
    }
}
