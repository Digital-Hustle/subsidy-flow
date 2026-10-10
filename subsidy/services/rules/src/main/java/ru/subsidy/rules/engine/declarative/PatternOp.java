package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;

/**
 * PATTERN: значение поля соответствует формату (ИНН — 10 или 12 цифр, кадастровый номер, …).
 * Параметры: {@code types}, {@code field}, {@code pattern}, {@code normalize} (digits | compact | none),
 * {@code hint} — как должно выглядеть значение, {@code checksum} (inn | ogrn) — ещё и контрольный разряд (ADR-025:
 * отдельным правилом WARNING, синтетические реквизиты открытого набора не валят пакет).
 */
public final class PatternOp implements DeclarativeOp {

    private static final String PATTERN = "pattern";
    private static final String NORMALIZE = "normalize";
    private static final String HINT = "hint";
    private static final String DIGITS = "digits";
    private static final String COMPACT = "compact";
    private static final String NONE = "none";
    private static final String CHECKSUM = "checksum";

    @Override
    public String name() {
        return "PATTERN";
    }

    @Override
    public String description() {
        return "Значение поля соответствует формату (регулярное выражение)";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, Params.FIELD, PATTERN, NORMALIZE, HINT, CHECKSUM);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
        Params.text(spec, Params.FIELD);
        Params.pattern(spec, PATTERN);
        String normalize = Params.text(spec, NORMALIZE, NONE);
        if (!List.of(DIGITS, COMPACT, NONE).contains(normalize)) {
            throw Params.invalid(spec, "normalize — digits, compact или none");
        }
        if (spec.params().has(CHECKSUM)
                && !List.of(Checksums.INN, Checksums.OGRN).contains(Params.text(spec, CHECKSUM))) {
            throw Params.invalid(spec, "checksum — inn или ogrn");
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        String field = Params.text(spec, Params.FIELD);
        Pattern pattern = Params.pattern(spec, PATTERN);
        String normalize = Params.text(spec, NORMALIZE, NONE);
        String checksum = Params.text(spec, CHECKSUM, "");
        List<String> bad = new ArrayList<>();
        List<Evidence> where = new ArrayList<>();
        int seen = 0;
        for (DocFacts doc : scope.docs(Params.types(spec))) {
            Optional<String> value = doc.text(field);
            if (value.isEmpty()) {
                continue;
            }
            seen++;
            String normalized = normalize(value.get(), normalize);
            if (!pattern.matcher(normalized).matches()
                    || !checksum.isEmpty() && !Checksums.valid(checksum, Docs.digits(normalized))) {
                bad.add(Docs.title(doc.type()) + " — «" + value.get() + "»");
                where.add(Evidence.of(doc, field));
            }
        }
        String label = Docs.field(field);
        if (seen == 0) {
            return spec.missing("Поле «" + label + "» не найдено в документах — проверить формат нельзя");
        }
        if (bad.isEmpty()) {
            return spec.pass(label + ": формат верный (" + seen + " знач.)", List.of());
        }
        String hint = Params.text(spec, HINT, "");
        return spec.fail("Неверный формат поля «" + label + "»: " + String.join("; ", bad)
                + (hint.isEmpty() ? "" : ". Ожидается: " + hint), where, hint.isEmpty() ? null : hint, null);
    }

    private static String normalize(String value, String mode) {
        String result = value.strip();
        if (DIGITS.equals(mode)) {
            result = Docs.digits(result);
        } else if (COMPACT.equals(mode)) {
            result = result.replaceAll("\\s+", "");
        }
        return result;
    }
}
