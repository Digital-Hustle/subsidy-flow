package ru.subsidy.rules.engine.declarative;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.money.Money;

/**
 * RANGE: сумма в пределах [min; max] (например, запрашиваемая сумма не больше максимума программы).
 * Параметры: {@code value} — список выражений-альтернатив, {@code min}, {@code max} (хотя бы одно).
 */
public final class RangeOp implements DeclarativeOp {

    private static final String VALUE = "value";
    private static final String MIN = "min";
    private static final String MAX = "max";

    @Override
    public String name() {
        return "RANGE";
    }

    @Override
    public String description() {
        return "Сумма не меньше min и не больше max";
    }

    @Override
    public List<String> parameters() {
        return List.of(VALUE, MIN, MAX);
    }

    @Override
    public void validate(RuleSpec spec) {
        Operand.list(spec, VALUE);
        BigDecimal min = Params.decimal(spec, MIN);
        BigDecimal max = Params.decimal(spec, MAX);
        if (min == null && max == null || min != null && max != null && min.compareTo(max) > 0) {
            throw Params.invalid(spec, "задайте min и/или max, min ≤ max");
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        List<Operand> values = Operand.list(spec, VALUE);
        Optional<Operand.Value> value = Operand.first(values, scope);
        if (value.isEmpty()) {
            return spec.missing("Нет данных: " + values.getFirst().left().label());
        }
        return check(spec, value.get(), Params.decimal(spec, MIN), Params.decimal(spec, MAX));
    }

    // границы — точно до копейки: допуск 0.01 у сравнения сумм здесь пропустил бы 3 000 000,01 при максимуме 3 000 000
    private static RuleResult check(RuleSpec spec, Operand.Value value, BigDecimal min, BigDecimal max) {
        BigDecimal amount = value.amount();
        String text = value.label() + Texts.SPACE + Money.format(amount);
        RuleResult result = spec.pass(text + " — в допустимых пределах", value.evidence());
        if (min != null && amount.compareTo(min) < 0) {
            result = spec.fail(text + " — меньше минимума " + Money.format(min), value.evidence(), Money.plain(min),
                    Money.plain(amount));
        } else if (max != null && amount.compareTo(max) > 0) {
            result = spec.fail(text + " — больше максимума " + Money.format(max), value.evidence(), Money.plain(max),
                    Money.plain(amount));
        }
        return result;
    }
}
