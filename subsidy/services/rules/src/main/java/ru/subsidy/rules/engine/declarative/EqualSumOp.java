package ru.subsidy.rules.engine.declarative;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.money.Money;

/**
 * EQUAL_SUM: суммы в двух документах совпадают (с допуском). Пары проверяются по порядку — решает первая пара,
 * для которой есть обе суммы. Параметры: {@code pairs} — [{left, right}], {@code tolerance} (по умолчанию 0.01).
 */
public final class EqualSumOp implements DeclarativeOp {

    private static final String PAIRS = "pairs";
    private static final String LEFT = "left";
    private static final String RIGHT = "right";
    private static final String TOLERANCE = "tolerance";

    @Override
    public String name() {
        return "EQUAL_SUM";
    }

    @Override
    public String description() {
        return "Суммы в двух документах совпадают";
    }

    @Override
    public List<String> parameters() {
        return List.of(PAIRS, TOLERANCE);
    }

    @Override
    public void validate(RuleSpec spec) {
        pairs(spec);
        BigDecimal tolerance = Params.decimal(spec, TOLERANCE);
        if (tolerance != null && tolerance.signum() < 0) {
            throw Params.invalid(spec, "tolerance не меньше 0");
        }
    }

    @Override
    public RuleResult apply(RuleSpec spec, Scope scope) {
        BigDecimal tolerance = Optional.ofNullable(Params.decimal(spec, TOLERANCE)).orElse(Money.TOLERANCE);
        List<Operand[]> pairs = pairs(spec);
        for (Operand[] pair : pairs) {
            Optional<Operand.Value> left = pair[0].resolve(scope);
            Optional<Operand.Value> right = pair[1].resolve(scope);
            if (left.isPresent() && right.isPresent()) {
                return compare(spec, left.get(), right.get(), tolerance);
            }
        }
        Operand[] first = pairs.getFirst();
        return spec.missing("Нет сумм для сверки: " + first[0].left().label() + " и " + first[1].left().label());
    }

    private static RuleResult compare(RuleSpec spec, Operand.Value left, Operand.Value right, BigDecimal tolerance) {
        List<Evidence> where = new ArrayList<>(left.evidence());
        where.addAll(right.evidence());
        BigDecimal diff = left.amount().subtract(right.amount()).abs();
        if (diff.compareTo(tolerance) <= 0) {
            return spec.pass(left.label() + Texts.SPACE + Money.format(left.amount()) + " совпадает с "
                    + right.label(), where);
        }
        return spec.fail(left.label() + Texts.SPACE + Money.format(left.amount()) + " ≠ " + right.label()
                + Texts.SPACE + Money.format(right.amount()) + " (разница " + Money.format(diff) + ")", where,
                Money.plain(right.amount()), Money.plain(left.amount()));
    }

    private static List<Operand[]> pairs(RuleSpec spec) {
        JsonNode node = spec.params().path(PAIRS);
        if (!node.isArray() || node.isEmpty()) {
            throw Params.invalid(spec, "pairs — список пар {left, right}");
        }
        List<Operand[]> out = new ArrayList<>();
        for (JsonNode pair : node) {
            out.add(new Operand[] {Operand.parse(spec, pair.path(LEFT).asText("")),
                Operand.parse(spec, pair.path(RIGHT).asText(""))});
        }
        return out;
    }
}
