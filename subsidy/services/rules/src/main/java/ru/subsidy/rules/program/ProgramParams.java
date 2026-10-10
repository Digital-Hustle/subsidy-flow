package ru.subsidy.rules.program;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.money.Money;

/**
 * Параметры программы из programs.params (ставка, максимум, сроки, ОКВЭД).
 *
 * @param raw JSON параметров
 */
public record ProgramParams(JsonNode raw) {

    /**
     * Сумма.
     *
     * @param key ключ
     * @return сумма или пусто
     */
    public Optional<BigDecimal> money(String key) {
        return text(key).map(Money::parse);
    }

    /**
     * Десятичное число (ставка).
     *
     * @param key ключ
     * @return число или пусто
     */
    public Optional<BigDecimal> decimal(String key) {
        return text(key).map(BigDecimal::new);
    }

    /**
     * Целое.
     *
     * @param key ключ
     * @return число или пусто
     */
    public Optional<Integer> integer(String key) {
        return Optional.ofNullable(raw.get(key)).filter(JsonNode::canConvertToInt).map(JsonNode::asInt);
    }

    /**
     * Дата.
     *
     * @param key ключ
     * @return дата или пусто
     */
    public Optional<LocalDate> date(String key) {
        return text(key).map(LocalDate::parse);
    }

    /**
     * Флаг.
     *
     * @param key ключ
     * @return значение (по умолчанию false)
     */
    public boolean flag(String key) {
        return raw.path(key).asBoolean(false);
    }

    /**
     * Список строк.
     *
     * @param key ключ
     * @return список
     */
    public List<String> list(String key) {
        List<String> out = new ArrayList<>();
        raw.path(key).forEach(node -> out.add(node.asText()));
        return out;
    }

    private Optional<String> text(String key) {
        return Optional.ofNullable(raw.get(key)).filter(node -> !node.isNull()).map(JsonNode::asText)
                .filter(text -> !text.isBlank());
    }
}
