package ru.subsidy.rules.engine;

import java.util.List;

import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * Детерминированное правило: факты пакета + параметры программы → результаты. Модель здесь не вызывается (ADR-006).
 */
public interface Rule {

    /**
     * Код правила из RULES-CATALOG.
     *
     * @return код
     */
    String code();

    /**
     * Проверка.
     *
     * @param facts факты пакета
     * @param params параметры программы
     * @return один или несколько результатов
     */
    List<RuleResult> evaluate(PackageFacts facts, ProgramParams params);
}
