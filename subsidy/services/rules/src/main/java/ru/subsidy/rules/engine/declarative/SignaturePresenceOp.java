package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.List;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.SignatureFacts;

/** Наличие ЭП PDF отдельно от обязательности; обязательность задаётся правилом вида документа. */
public final class SignaturePresenceOp implements DeclarativeOp {

    private static final String REQUIRED = "required";

    @Override
    public String name() {
        return "SIGNATURE_PRESENCE";
    }

    @Override
    public String description() {
        return "Наличие ЭП PDF без проверки действительности; required задаёт обязательность";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, REQUIRED);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
        if (spec.params().has(REQUIRED) && !spec.params().path(REQUIRED).isBoolean()) {
            throw Params.invalid(spec, "required — boolean");
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        List<DocFacts> docs = scope.docs(Params.types(spec));
        if (docs.isEmpty()) {
            return spec.notApplicable("Документов этих видов нет");
        }
        boolean required = spec.params().path(REQUIRED).asBoolean(false);
        boolean unknown = false;
        boolean absent = false;
        List<String> actual = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        for (DocFacts doc : docs) {
            SignatureFacts signature = scope.facts().files().stream().filter(file -> file.fileId().equals(doc.fileId()))
                    .map(file -> file.electronicSignature()).findFirst().orElse(SignatureFacts.stored(null, null));
            unknown |= "UNKNOWN".equals(signature.status());
            absent |= "ABSENT".equals(signature.status());
            actual.add(Docs.title(doc.type()) + " [PDF " + doc.fileId() + "]: " + signature.status()
                    + (signature.reason() == null ? "" : " (" + signature.reason() + ")"));
            evidence.add(Evidence.of(doc, "electronicSignature"));
        }
        String status = required && absent ? spec.severity() : unknown ? RuleStatus.DATA_MISSING : RuleStatus.PASSED;
        return new RuleResult(spec.code(), status, "ЭП всего PDF (не конкретной страницы): "
                + String.join(Texts.SEMI, actual) + ". Действительность не проверена; наличие "
                + (required ? "обязательно по правилу" : "показано справочно"), evidence,
                required ? "PRESENT" : "INFORMATIONAL", String.join(Texts.SEMI, actual));
    }
}
