package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.money.Money;

/**
 * Суммы строк таблиц для правил.
 */
final class Sums {

    private Sums() {
    }

    /**
     * Сумма строки таблицы.
     *
     * @param row строка
     * @param key ключ
     * @return сумма или пусто
     */
    static Optional<BigDecimal> money(JsonNode row, String key) {
        return Optional.ofNullable(row.get(key)).filter(node -> !node.isNull()).map(JsonNode::asText)
                .map(Money::parse);
    }
}
