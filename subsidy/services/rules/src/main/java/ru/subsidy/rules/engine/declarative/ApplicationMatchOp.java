package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;

/** Сверка поля сохранённой заявки с относящимися к заявителю полями документов. */
public final class ApplicationMatchOp implements DeclarativeOp {

    private static final String PATH = "applicationPath";
    private static final String WHEN = "whenPath";
    private static final String VALUE = "whenValue";
    private static final String NORMALIZE = "normalize";
    private static final String TEXT = "text";
    private static final String EMPTY = "не найдено";
    private static final String DIGITS = "digits";
    private static final String COMPACT = "compact";
    private static final String SPACE = " ";
    private static final String WHITESPACE = "\\s+";
    private static final int ARRAY_SUFFIX_LENGTH = 3;

    @Override
    public String name() {
        return "APPLICATION_MATCH";
    }

    @Override
    public String description() {
        return "Сверка документа с сохранённой заявкой; обе стороны показаны в протоколе";
    }

    @Override
    public List<String> parameters() {
        return List.of(PATH, Params.FIELDS, NORMALIZE, WHEN, VALUE);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.text(spec, PATH);
        JsonNode fields = spec.params().path(Params.FIELDS);
        if (!fields.isObject() || fields.isEmpty()) {
            throw Params.invalid(spec, "fields — объект ВИД: [поле, альтернативное поле]");
        }
        fields.fields().forEachRemaining(entry -> {
            Params.requireType(spec, entry.getKey());
            if (Scope.ANY.equals(entry.getKey()) || Params.strings(entry.getValue()).isEmpty()) {
                throw Params.invalid(spec, "fields — конкретные виды и непустые списки полей");
            }
        });
        if (!List.of(TEXT, DIGITS, COMPACT).contains(Params.text(spec, NORMALIZE, TEXT))) {
            throw Params.invalid(spec, "normalize — text, digits или compact");
        }
        if (spec.params().has(WHEN)) {
            Params.text(spec, WHEN);
            Params.text(spec, VALUE);
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        if (!condition(spec, scope)) {
            return spec.notApplicable("Условие сверки не выполнено для этой заявки");
        }
        String expected = application(scope, Params.text(spec, PATH));
        JsonNode fields = spec.params().path(Params.FIELDS);
        List<String> types = new ArrayList<>();
        fields.fieldNames().forEachRemaining(types::add);
        List<Comparison> compared = scope.docs(types).stream().map(doc -> compare(spec, expected, doc)).toList();
        if (compared.isEmpty()) {
            return spec.notApplicable("Документов для этой сверки нет");
        }
        String actual = String.join(Texts.SEMI, compared.stream().map(Comparison::actual).toList());
        List<Evidence> evidence = compared.stream().flatMap(value -> value.evidence().stream()).toList();
        return new RuleResult(spec.code(), status(spec, expected, compared), spec.title() + ": в заявке — "
                + (expected.isBlank() ? EMPTY : expected) + "; в документах — " + actual,
                evidence, expected.isBlank() ? null : expected, actual);
    }

    private static boolean condition(RuleSpec spec, Scope scope) {
        return !spec.params().has(WHEN) || Params.text(spec, VALUE).equals(application(scope, Params.text(spec, WHEN)));
    }

    private static String status(RuleSpec spec, String expected, List<Comparison> compared) {
        if (compared.stream().anyMatch(Comparison::mismatch)) {
            return spec.severity();
        }
        return expected.isBlank() || compared.stream().anyMatch(Comparison::missing)
                ? RuleStatus.DATA_MISSING : RuleStatus.PASSED;
    }

    private static Comparison compare(RuleSpec spec, String expected, DocFacts doc) {
        List<Evidence> evidence = new ArrayList<>();
        List<String> values = values(doc, Params.strings(spec.params().path(Params.FIELDS).path(doc.type())), evidence);
        String actual = Docs.title(doc.type()) + " [" + doc.documentId() + "]: "
                + (values.isEmpty() ? EMPTY : String.join(" | ", values));
        boolean mismatch = !expected.isBlank() && !values.isEmpty() && values.stream()
                .noneMatch(value -> normalize(spec, value).equals(normalize(spec, expected)));
        return new Comparison(actual, evidence, values.isEmpty(), mismatch);
    }

    private static String application(Scope scope, String path) {
        JsonNode node = scope.facts().application();
        for (String part : path.split("\\.")) {
            if (node == null) {
                return "";
            }
            node = node.path(part);
        }
        return node == null || node.isNull() ? "" : node.asText("").strip();
    }

    private static List<String> values(DocFacts doc, List<String> fields, List<Evidence> evidence) {
        List<String> values = new ArrayList<>();
        for (String field : fields) {
            int array = field.indexOf("[].");
            if (array < 0) {
                doc.text(field).filter(value -> !value.isBlank()).ifPresent(value -> values.add(value));
            } else {
                doc.rows(field.substring(0, array)).stream()
                        .map(row -> row.path(field.substring(array + ARRAY_SUFFIX_LENGTH)))
                        .filter(JsonNode::isTextual).map(JsonNode::asText).filter(value -> !value.isBlank())
                        .forEach(values::add);
            }
            evidence.add(Evidence.of(doc, field));
        }
        return values;
    }

    private static String normalize(RuleSpec spec, String value) {
        String mode = Params.text(spec, NORMALIZE, TEXT);
        if (DIGITS.equals(mode)) {
            return Docs.digits(value);
        }
        String text = value.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return COMPACT.equals(mode) ? text.replaceAll(WHITESPACE, "")
                : text.replaceAll("[^\\p{L}\\p{N}]+", SPACE).strip().replaceAll(WHITESPACE, SPACE);
    }

    private record Comparison(String actual, List<Evidence> evidence, boolean missing, boolean mismatch) {
    }
}
