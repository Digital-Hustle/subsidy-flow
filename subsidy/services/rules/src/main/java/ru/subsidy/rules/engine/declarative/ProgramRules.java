package ru.subsidy.rules.engine.declarative;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;

/**
 * Правила программы как данные: разбор с понятными ошибками и выполнение. Сбой одного правила — ERROR,
 * остальные выполняются.
 */
public final class ProgramRules {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramRules.class);
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final String RULE = "Правило ";

    private final Map<String, DeclarativeOp> ops = new LinkedHashMap<>();

    /**
     * Все операции.
     */
    public ProgramRules() {
        for (DeclarativeOp operation : List.of(new RequiredFieldsOp(), new PatternOp(), new DateNotFutureOp(),
                new NotExpiredOp(), new SameValueOp(), new RatioMinOp(), new EqualSumOp(), new NotEmptyOp(),
                new RangeOp())) {
            ops.put(operation.name(), operation);
        }
    }

    /**
     * Операции для редактора правил.
     *
     * @return операции в порядке объявления
     */
    public List<DeclarativeOp> operations() {
        return List.copyOf(ops.values());
    }

    /**
     * Разбирает и проверяет правила программы.
     *
     * @param rules массив правил (или отсутствующий узел — правил нет)
     * @return правила
     * @throws IllegalArgumentException с причиной по-русски
     */
    public List<RuleSpec> parse(JsonNode rules) {
        if (rules == null || rules.isMissingNode() || rules.isNull()) {
            return List.of();
        }
        if (!rules.isArray()) {
            throw new IllegalArgumentException("rules — массив правил");
        }
        List<RuleSpec> out = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode node : rules) {
            RuleSpec spec = spec(node);
            if (!codes.add(spec.code())) {
                throw new IllegalArgumentException(RULE + spec.code() + " встречается дважды");
            }
            ops.get(spec.op()).validate(spec);
            out.add(spec);
        }
        return out;
    }

    /**
     * Выполняет включённые правила.
     *
     * @param specs правила
     * @param scope факты и дата
     * @return результаты
     */
    public List<RuleResult> run(List<RuleSpec> specs, Scope scope) {
        List<RuleResult> results = new ArrayList<>();
        for (RuleSpec spec : specs) {
            if (spec.enabled()) {
                results.add(safe(spec, scope));
            }
        }
        return results;
    }

    private RuleResult safe(RuleSpec spec, Scope scope) {
        try {
            return ops.get(spec.op()).apply(spec, scope);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException
                 | IndexOutOfBoundsException failure) {
            LOG.warn("program rule {} failed package={}: {}", spec.code(), scope.facts().packageId(),
                    failure.getClass().getSimpleName());
            return new RuleResult(spec.code(), RuleStatus.ERROR, "Правило «" + spec.title()
                    + "» не выполнилось — проверьте вручную", List.of(), null, null);
        }
    }

    private RuleSpec spec(JsonNode node) {
        String code = node.path("code").asText("").strip();
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Код правила «" + code + "»: латиница A-Z, цифры и _, например INN_SAME");
        }
        String operation = node.path("op").asText("").strip();
        if (!ops.containsKey(operation)) {
            throw new IllegalArgumentException(RULE + code + ": неизвестная операция «" + operation + "», доступны "
                    + String.join(", ", ops.keySet()));
        }
        String severity = node.path("severity").asText(RuleStatus.FAILED).strip();
        if (!RuleStatus.FAILED.equals(severity) && !RuleStatus.WARNING.equals(severity)) {
            throw new IllegalArgumentException(RULE + code + ": severity — FAILED или WARNING");
        }
        String title = node.path("title").asText("").strip();
        return new RuleSpec(code, title.isEmpty() ? code : title, operation, severity, node.path("enabled").asBoolean(true),
                node);
    }
}
