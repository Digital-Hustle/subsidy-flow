package ru.subsidy.rules.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * Запускает все правила; исключение одного — результат ERROR, остальные выполняются.
 */
@Component
public class RuleEngine {

    /** Версия набора правил (в протоколе). */
    public static final String RULES_VERSION = "2026.10-3";

    private static final Logger LOG = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;

    /**
     * Конструктор.
     *
     * @param rules все правила
     */
    public RuleEngine(List<Rule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparing(Rule::code)).toList();
    }

    /**
     * Проверяет пакет.
     *
     * @param facts факты
     * @param params параметры программы
     * @return результаты всех правил
     */
    public List<RuleResult> run(PackageFacts facts, ProgramParams params) {
        List<RuleResult> results = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                results.addAll(rule.evaluate(facts, params));
            } catch (RuntimeException failure) {
                LOG.warn("rule {} failed package={}: {}", rule.code(), facts.packageId(),
                        failure.getClass().getSimpleName());
                results.add(new RuleResult(rule.code(), RuleStatus.ERROR, "Правило не выполнилось — проверьте вручную",
                        List.of(), null, null));
            }
        }
        return results;
    }

    /**
     * Коды правил.
     *
     * @return коды
     */
    public List<String> codes() {
        return rules.stream().map(Rule::code).toList();
    }
}
