package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;

/**
 * REQUIRED_FIELDS: в документе вычитан минимум полей, иначе он считается неразобранным.
 * Параметры: {@code fields} — объект «вид → список полей».
 */
public final class RequiredFieldsOp implements DeclarativeOp {

    private static final String FIELDS = "fields";

    @Override
    public String name() {
        return "REQUIRED_FIELDS";
    }

    @Override
    public String description() {
        return "Минимум полей по видам документов: без них документ считается неразобранным";
    }

    @Override
    public List<String> parameters() {
        return List.of(FIELDS);
    }

    @Override
    public void validate(RuleSpec spec) {
        JsonNode fields = spec.params().path(FIELDS);
        if (!fields.isObject() || fields.isEmpty()) {
            throw Params.invalid(spec, "fields — объект «вид: [поля]»");
        }
        fields.fieldNames().forEachRemaining(type -> {
            if (!Docs.known(type) || !fields.path(type).isArray() || fields.path(type).isEmpty()) {
                throw Params.invalid(spec, "для вида " + type + " нужен непустой список полей");
            }
        });
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        List<String> problems = new ArrayList<>();
        List<Evidence> where = new ArrayList<>();
        int checked = 0;
        Iterator<Map.Entry<String, JsonNode>> byType = spec.params().path(FIELDS).properties().iterator();
        while (byType.hasNext()) {
            Map.Entry<String, JsonNode> entry = byType.next();
            List<String> required = new ArrayList<>();
            entry.getValue().forEach(field -> required.add(field.asText()));
            for (DocFacts doc : scope.docs(List.of(entry.getKey()))) {
                checked++;
                List<String> lacking = required.stream().filter(field -> doc.field(field).isEmpty()).toList();
                if (!lacking.isEmpty()) {
                    problems.add(Docs.title(doc.type()) + " (стр. " + doc.fromPage() + "): не вычитаны "
                            + String.join(", ", lacking.stream().map(Docs::field).toList()));
                    where.add(new Evidence(doc.documentId(), lacking.getFirst(), doc.fromPage()));
                }
            }
        }
        if (checked == 0) {
            return spec.notApplicable("Нет документов, для которых задан минимум полей");
        }
        if (problems.isEmpty()) {
            return spec.pass("Минимум полей вычитан во всех документах (" + checked + ")", List.of());
        }
        return spec.fail("Документ не разобран — " + String.join("; ", problems)
                + ". Проверьте оригинал и впишите значения вручную", where, null, null);
    }
}
