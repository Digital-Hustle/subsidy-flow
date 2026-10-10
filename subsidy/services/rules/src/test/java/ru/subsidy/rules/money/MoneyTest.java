package ru.subsidy.rules.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {"1500000.00;1500000.00", "1 500 000,00;1500000.00", "1500000;1500000.00",
        "12,345;12345.00", "1.234.567,89;1234567.89"})
    void parse_scale2HalfUp(String raw, String expected) {
        assertThat(Money.parse(raw)).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void round_halfUp() {
        assertThat(Money.round(new BigDecimal("0.005"))).isEqualTo(new BigDecimal("0.01"));
        assertThat(Money.round(new BigDecimal("0.004"))).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void same_withinOneKopeck() {
        assertThat(Money.same(new BigDecimal("100.00"), new BigDecimal("100.01"))).isTrue();
        assertThat(Money.same(new BigDecimal("100.00"), new BigDecimal("100.02"))).isFalse();
        assertThat(Money.greater(new BigDecimal("100.02"), new BigDecimal("100.00"))).isTrue();
        assertThat(Money.greater(new BigDecimal("100.01"), new BigDecimal("100.00"))).isFalse();
    }

    @Test
    void format_forSpecialist() {
        assertThat(Money.format(new BigDecimal("1500000"))).isEqualTo("1 500 000,00");
        assertThat(Money.parse("нет")).isNull();
    }
}
