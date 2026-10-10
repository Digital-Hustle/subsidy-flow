package ru.subsidy.rules.engine.declarative;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;

/**
 * DATE_NOT_FUTURE: дата документа не позже сегодняшней. Параметры: {@code types}, {@code field} (по умолчанию date).
 */
public final class DateNotFutureOp implements DeclarativeOp {

    private static final String DATE = "date";

    @Override
    public String name() {
        return "DATE_NOT_FUTURE";
    }

    @Override
    public String description() {
        return "Дата документа не в будущем";
    }

    @Override
    public List<String> parameters() {
        return List.of(Params.TYPES, Params.FIELD);
    }

    @Override
    public void validate(RuleSpec spec) {
        Params.types(spec);
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        String field = Params.text(spec, Params.FIELD, DATE);
        List<String> future = new ArrayList<>();
        List<Evidence> where = new ArrayList<>();
        int seen = 0;
        for (DocFacts doc : scope.docs(Params.types(spec))) {
            Optional<LocalDate> date = doc.date(field);
            if (date.isEmpty()) {
                continue;
            }
            seen++;
            if (date.get().isAfter(scope.today())) {
                future.add(Docs.title(doc.type()) + Texts.DASH + Texts.date(date.get()));
                where.add(Evidence.of(doc, field));
            }
        }
        if (seen == 0) {
            return spec.missing("Даты документов не найдены — проверить нельзя");
        }
        if (future.isEmpty()) {
            return spec.pass("Даты документов не в будущем (" + seen + " знач.)", List.of());
        }
        return spec.fail("Дата в будущем (сегодня " + Texts.date(scope.today()) + "): "
                + String.join(Texts.SEMI, future), where, "не позже " + Texts.date(scope.today()), null);
    }
}
