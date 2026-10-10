package ru.subsidy.rules.engine.rules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.engine.declarative.ProgramRules;
import ru.subsidy.rules.engine.declarative.RuleSpec;
import ru.subsidy.rules.engine.declarative.Scope;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * Правила версии программы ({@code params.rules}): ИНН, даты, сроки, доля собственных средств, суммы — данные,
 * которые администратор меняет без правки кода. Каждое правило даёт отдельный результат со своим кодом.
 */
@Component
public class ProgramRulesRule implements Rule {

    /** Ключ правил в параметрах программы. */
    public static final String RULES = "rules";

    private static final String CODE = "PROGRAM_RULES";

    private final ProgramRules programRules = new ProgramRules();
    private final Clock clock;
    private final RulesProperties props;

    /**
     * Конструктор.
     *
     * @param clock часы
     * @param props настройки (часовой пояс)
     */
    public ProgramRulesRule(Clock clock, RulesProperties props) {
        this.clock = clock;
        this.props = props;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleSpec> specs;
        try {
            specs = programRules.parse(params.raw().path(RULES));
        } catch (IllegalArgumentException broken) {
            return List.of(new RuleResult(CODE, RuleStatus.ERROR, "Правила программы заданы с ошибкой: "
                    + broken.getMessage(), List.of(), null, null));
        }
        LocalDate today = LocalDate.now(clock.withZone(props.zone()));
        return programRules.run(specs, new Scope(facts, today));
    }
}
