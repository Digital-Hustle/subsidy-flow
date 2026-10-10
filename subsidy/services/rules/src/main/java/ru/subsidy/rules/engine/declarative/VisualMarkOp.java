package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.List;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;

/**
 * VISUAL_MARK: видимая подпись (или печать) на документе по результату vision-модели docproc (B2-2, FR-33).
 * Поле {@code field} — {@code visualSignature} / {@code visualStamp}: DETECTED, NOT_DETECTED, UNDETERMINED;
 * специалист может подтвердить значение правкой поля. {@code acceptElectronic=true} — ЭП в PDF тоже считается
 * подписью. NOT_DETECTED → severity правила; не определено → замечание WARNING «проверить вручную» (не переводит
 * весь пакет в ручную проверку: DOCX/XLSX и недоступная vision-модель не должны ломать протокол).
 */
public final class VisualMarkOp implements DeclarativeOp {

    private static final String ACCEPT_ELECTRONIC = "acceptElectronic";
    private static final String DETECTED = "DETECTED";
    private static final String NOT_DETECTED = "NOT_DETECTED";
    private static final String PRESENT = "PRESENT";
    private static final String COLON = ": ";
    private static final String DOT = ". ";

    @Override
    public String name() {
        return "VISUAL_MARK";
    }

    @Override
    public String description() {
        return "Видимая подпись/печать на изображении документа (vision-модель); ЭП PDF может её заменять";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, Params.FIELD, ACCEPT_ELECTRONIC);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
        if (!List.of("visualSignature", "visualStamp").contains(Params.text(spec, Params.FIELD))) {
            throw Params.invalid(spec, "field — visualSignature или visualStamp");
        }
        if (spec.params().has(ACCEPT_ELECTRONIC) && !spec.params().path(ACCEPT_ELECTRONIC).isBoolean()) {
            throw Params.invalid(spec, "acceptElectronic — boolean");
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        List<DocFacts> docs = scope.docs(Params.types(spec));
        if (docs.isEmpty()) {
            return spec.notApplicable("Документов этих видов нет");
        }
        String field = Params.text(spec, Params.FIELD);
        boolean electronic = spec.params().path(ACCEPT_ELECTRONIC).asBoolean(false);
        Found sorted = new Found(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        docs.forEach(doc -> sort(scope, doc, field, electronic, sorted));
        List<String> found = sorted.found();
        List<String> absent = sorted.absent();
        List<String> unknown = sorted.unknown();
        List<Evidence> evidence = sorted.evidence();
        String label = Docs.field(field);
        String actual = summary(found, absent, unknown);
        String status = !absent.isEmpty() ? spec.severity()
                : !unknown.isEmpty() ? RuleStatus.WARNING : RuleStatus.PASSED;
        String message = !absent.isEmpty()
                ? "Не обнаружена " + label + COLON + String.join(Texts.SEMI, absent) + DOT + actual
                : !unknown.isEmpty()
                ? "Не удалось определить " + label + COLON + String.join(Texts.SEMI, unknown)
                        + " (нет изображения страницы, нечитаемо или DOCX/XLSX) — проверьте оригинал"
                        + " и подтвердите поле вручную"
                : label + COLON + String.join(Texts.SEMI, found);
        return new RuleResult(spec.code(), status, message, evidence, DETECTED, actual);
    }

    private static void sort(Scope scope, DocFacts doc, String field, boolean electronic, Found sorted) {
        String title = Docs.title(doc.type());
        String value = doc.text(field).orElse("");
        if (electronic && hasElectronicSignature(scope, doc)) {
            sorted.found().add(title + " — ЭП в PDF");
        } else if (DETECTED.equals(value)) {
            sorted.found().add(title + " — видна" + confidence(doc, field));
        } else {
            (NOT_DETECTED.equals(value) ? sorted.absent() : sorted.unknown()).add(title
                    + (NOT_DETECTED.equals(value) ? confidence(doc, field) : ""));
            sorted.evidence().add(Evidence.of(doc, field));
        }
    }

    private static boolean hasElectronicSignature(Scope scope, DocFacts doc) {
        return scope.facts().files().stream().filter(file -> file.fileId().equals(doc.fileId()))
                .anyMatch(file -> PRESENT.equals(file.electronicSignature().status()));
    }

    private static String confidence(DocFacts doc, String field) {
        return doc.field(field).filter(fact -> fact.confidence() != null)
                .map(fact -> " (уверенность " + fact.confidence().stripTrailingZeros().toPlainString() + ")").orElse("");
    }

    private static String summary(List<String> found, List<String> absent, List<String> unknown) {
        List<String> parts = new ArrayList<>();
        if (!found.isEmpty()) {
            parts.add("есть" + COLON + String.join(Texts.SEMI, found));
        }
        if (!absent.isEmpty()) {
            parts.add("нет" + COLON + String.join(Texts.SEMI, absent));
        }
        if (!unknown.isEmpty()) {
            parts.add("не определено" + COLON + String.join(Texts.SEMI, unknown));
        }
        return String.join(DOT, parts);
    }

    private record Found(List<String> found, List<String> absent, List<String> unknown, List<Evidence> evidence) {
    }
}
