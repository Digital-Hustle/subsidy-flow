package ru.subsidy.rules.engine.declarative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.money.Money;

/**
 * RATIO_MIN: числитель / знаменатель ≥ min (например, собственные средства ≥ 50 % расходов). BigDecimal на сервере.
 * Параметры: {@code numerator}, {@code denominator} — списки выражений-альтернатив, {@code min} — порог доли.
 */
public final class RatioMinOp implements DeclarativeOp {

    private static final String NUMERATOR = "numerator";
    private static final String DENOMINATOR = "denominator";
    private static final String MIN = "min";
    private static final int SCALE = 6;

    @Override
    public String name() {
        return "RATIO_MIN";
    }

    @Override
    public String description() {
        return "Доля одной суммы в другой не меньше порога";
    }

    @Override
    public List<String> parameters() {
        return List.of(NUMERATOR, DENOMINATOR, MIN);
    }

    @Override
    public void validate(RuleSpec spec) {
        Operand.list(spec, NUMERATOR);
        Operand.list(spec, DENOMINATOR);
        BigDecimal min = Params.decimal(spec, MIN);
        if (min == null || min.signum() < 0 || min.compareTo(BigDecimal.ONE) > 0) {
            throw Params.invalid(spec, "min — доля от 0 до 1, например 0.5");
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        List<Operand> numerators = Operand.list(spec, NUMERATOR);
        List<Operand> denominators = Operand.list(spec, DENOMINATOR);
        Optional<Operand.Value> part = Operand.first(numerators, scope);
        Optional<Operand.Value> whole = Operand.first(denominators, scope);
        RuleResult result;
        if (part.isEmpty() || whole.isEmpty()) {
            result = spec.missing("Нет данных для расчёта: " + (part.isEmpty() ? numerators : denominators)
                    .getFirst().left().label());
        } else if (whole.get().amount().signum() <= 0) {
            result = spec.missing(whole.get().label() + " — ноль или меньше, долю посчитать нельзя");
        } else {
            result = ratio(spec, part.get(), whole.get(), Params.decimal(spec, MIN));
        }
        return result;
    }

    private static RuleResult ratio(RuleSpec spec, Operand.Value part, Operand.Value whole, BigDecimal min) {
        BigDecimal ratio = part.amount().divide(whole.amount(), SCALE, RoundingMode.DOWN);
        String text = part.label() + Texts.SPACE + Money.format(part.amount()) + " из " + whole.label()
                + Texts.SPACE + Money.format(whole.amount()) + " = " + Texts.percent(ratio);
        List<Evidence> where = new ArrayList<>(part.evidence());
        where.addAll(whole.evidence());
        // сравнение без округления доли: 499 999,99 из 1 000 000 — это меньше 50 %, хотя 0.4999999 округляется до 0.500000
        if (part.amount().compareTo(whole.amount().multiply(min)) >= 0) {
            return spec.pass(text + " — не меньше " + Texts.percent(min), where);
        }
        return spec.fail(text + " — меньше требуемых " + Texts.percent(min), where, Texts.percent(min),
                Texts.percent(ratio));
    }
}
