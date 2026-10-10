package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.List;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;

/**
 * NOT_EMPTY: у документа есть содержимое — заполнено хотя бы одно из полей.
 * Параметры: {@code types}, {@code fields}.
 */
public final class NotEmptyOp implements DeclarativeOp {

    @Override
    public String name() {
        return "NOT_EMPTY";
    }

    @Override
    public String description() {
        return "Документ не пустой: заполнено хотя бы одно из полей";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, Params.FIELDS);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
        Params.list(spec, Params.FIELDS);
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        List<String> fields = Params.list(spec, Params.FIELDS);
        List<DocFacts> docs = scope.docs(Params.types(spec));
        if (docs.isEmpty()) {
            return spec.notApplicable("Таких документов в пакете нет");
        }
        List<String> empty = new ArrayList<>();
        List<Evidence> where = new ArrayList<>();
        for (DocFacts doc : docs) {
            if (fields.stream().allMatch(field -> doc.text(field).isEmpty())) {
                empty.add(Docs.title(doc.type()) + " (стр. " + doc.fromPage() + ")");
                where.add(new Evidence(doc.documentId(), fields.getFirst(), doc.fromPage()));
            }
        }
        if (empty.isEmpty()) {
            return spec.pass("Содержимое есть: " + Texts.fields(fields), List.of());
        }
        return spec.fail("Пустое содержимое — не найдено: " + Texts.fields(fields) + ". " + String.join(Texts.SEMI,
                empty), where, null, null);
    }
}
