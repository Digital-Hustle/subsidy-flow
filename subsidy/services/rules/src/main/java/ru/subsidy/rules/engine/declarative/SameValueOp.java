package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;

/**
 * SAME_VALUE: значение поля одинаково во всех документах пакета (например, ИНН заявителя).
 * Параметры: {@code types}, {@code field}, {@code includeApplicant} — сверять и с ИНН из карточки заявки.
 */
public final class SameValueOp implements DeclarativeOp {

    private static final String INCLUDE_APPLICANT = "includeApplicant";
    private static final String CARD = "Карточка заявки";

    @Override
    public String name() {
        return "SAME_VALUE";
    }

    @Override
    public String description() {
        return "Значение поля одинаково во всех документах пакета";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, Params.FIELD, INCLUDE_APPLICANT);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
        Params.text(spec, Params.FIELD);
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        String field = Params.text(spec, Params.FIELD);
        Map<String, List<String>> byValue = new LinkedHashMap<>();
        List<Evidence> where = new ArrayList<>();
        if (spec.params().path(INCLUDE_APPLICANT).asBoolean(false) && scope.facts().inn() != null
                && !scope.facts().inn().isBlank()) {
            byValue.computeIfAbsent(Docs.digits(scope.facts().inn()), key -> new ArrayList<>()).add(CARD);
        }
        for (DocFacts doc : scope.docs(Params.types(spec))) {
            doc.text(field).map(Docs::digits).filter(value -> !value.isEmpty()).ifPresent(value -> {
                byValue.computeIfAbsent(value, key -> new ArrayList<>()).add(Docs.title(doc.type()));
                where.add(Evidence.of(doc, field));
            });
        }
        String label = Docs.field(field);
        int sources = byValue.values().stream().mapToInt(List::size).sum();
        RuleResult result;
        if (sources == 0) {
            result = spec.missing("Поле «" + label + "» не найдено в документах");
        } else if (sources == 1) {
            result = spec.notApplicable(label + " найден только в одном месте — сверять не с чем");
        } else if (byValue.size() == 1) {
            result = spec.pass(label + " " + byValue.keySet().iterator().next() + " совпадает во всех документах ("
                    + sources + ")", where);
        } else {
            List<String> parts = new ArrayList<>();
            byValue.forEach((value, docs) -> parts.add(value + " — " + String.join(", ", docs)));
            result = spec.fail(label + " различается между документами: " + String.join(Texts.SEMI, parts),
                    where, byValue.keySet().iterator().next(), null);
        }
        return result;
    }
}
