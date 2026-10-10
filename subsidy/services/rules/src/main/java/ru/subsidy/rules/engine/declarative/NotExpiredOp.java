package ru.subsidy.rules.engine.declarative;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;

/**
 * NOT_EXPIRED: срок действия документа не истёк; истекает в ближайшие {@code warnDays} дней — предупреждение.
 * Параметры: {@code types}, {@code field} (по умолчанию validUntil), {@code warnDays}.
 */
public final class NotExpiredOp implements DeclarativeOp {

    private static final String VALID_UNTIL = "validUntil";
    private static final String WARN_DAYS = "warnDays";

    @Override
    public String name() {
        return "NOT_EXPIRED";
    }

    @Override
    public String description() {
        return "Срок действия не истёк; скоро истекает — предупреждение";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, Params.FIELD, WARN_DAYS);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
        Params.count(spec, WARN_DAYS, 0);
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        String field = Params.text(spec, Params.FIELD, VALID_UNTIL);
        int warnDays = Params.count(spec, WARN_DAYS, 0);
        List<DocFacts> docs = scope.docs(Params.types(spec));
        if (docs.isEmpty()) {
            return spec.notApplicable("Документов со сроком действия в пакете нет");
        }
        List<String> expired = new ArrayList<>();
        List<String> soon = new ArrayList<>();
        List<Evidence> where = new ArrayList<>();
        for (DocFacts doc : docs) {
            Optional<LocalDate> until = doc.date(field);
            if (until.isPresent()) {
                long left = ChronoUnit.DAYS.between(scope.today(), until.get());
                String text = Docs.title(doc.type()) + " — до " + Texts.date(until.get());
                if (left < 0) {
                    expired.add(text);
                    where.add(Evidence.of(doc, field));
                } else if (left <= warnDays) {
                    soon.add(text + " (осталось " + left + " дн.)");
                    where.add(Evidence.of(doc, field));
                }
            }
        }
        return verdict(spec, docs, field, expired, soon, where);
    }

    private static RuleResult verdict(RuleSpec spec, List<DocFacts> docs, String field, List<String> expired,
                                      List<String> soon, List<Evidence> where) {
        long dated = docs.stream().filter(doc -> doc.date(field).isPresent()).count();
        RuleResult result;
        if (!expired.isEmpty()) {
            result = spec.fail("Срок действия истёк: " + String.join(Texts.SEMI, expired), where, null, null);
        } else if (dated == 0) {
            result = spec.missing("Срок действия не найден в документах: " + docs.stream()
                    .map(doc -> Docs.title(doc.type())).distinct().collect(Collectors.joining(", ")));
        } else if (!soon.isEmpty()) {
            result = spec.warn("Срок действия скоро истечёт: " + String.join(Texts.SEMI, soon), where);
        } else {
            result = spec.pass("Сроки действия не истекли (" + dated + " док.)", List.of());
        }
        return result;
    }
}
