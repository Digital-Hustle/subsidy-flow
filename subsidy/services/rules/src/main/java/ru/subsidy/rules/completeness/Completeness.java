package ru.subsidy.rules.completeness;

import java.util.List;

import ru.subsidy.rules.engine.RuleResult;

/**
 * Комплектность пакета.
 *
 * @param items пункты
 * @param missing чего не хватает
 * @param percent доля выполненных применимых пунктов, %
 * @param results правила CHK_*
 * @param request текст запроса организации о недостающем
 */
public record Completeness(List<ItemResult> items, List<Missing> missing, int percent, List<RuleResult> results,
                           String request) {
}
