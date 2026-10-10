package ru.subsidy.docproc.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class NormalizersTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "1 234 567,89 руб.;1234567.89",
        "1234567.89;1234567.89",
        "1.234.567,89;1234567.89",
        "12 000 руб. 05 коп.;12000.05",
        "3 000 000;3000000.00",
        "1500-50;1500.50",
        "0,1;0.10"})
    void money_parsesRussianFormats(String raw, String expected) {
        assertThat(MoneyNormalizer.parse(raw)).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(MoneyNormalizer.parse(raw).scale()).isEqualTo(2);
    }

    @Test
    void money_rejectsText() {
        assertThat(MoneyNormalizer.parse("нет данных")).isNull();
        assertThat(MoneyNormalizer.parse("")).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "01.02.2026;2026-02-01",
        "«1» февраля 2026 г.;2026-02-01",
        "15 марта 2026;2026-03-15",
        "3 мая 2025 года;2025-05-03",
        "2026-10-09;2026-10-09",
        "09.10.26;2026-10-09"})
    void date_parsesRussianFormats(String raw, String expected) {
        assertThat(DateNormalizer.parse(raw)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void date_rejectsImpossible() {
        assertThat(DateNormalizer.parse("31.02.2026")).isNull();
    }

    @Test
    void requisites_checksums() {
        assertThat(Requisites.inn("7707083893")).isTrue();
        assertThat(Requisites.inn("7707083894")).isFalse();
        assertThat(Requisites.inn("500100732259")).isTrue();
        assertThat(Requisites.inn("500100732258")).isFalse();
        assertThat(Requisites.ogrn("1027700132195")).isTrue();
        assertThat(Requisites.ogrn("1027700132196")).isFalse();
        assertThat(Requisites.ogrn("304500116000157")).isTrue();
        assertThat(Requisites.kpp("773601001")).isTrue();
        assertThat(Requisites.kpp("77360100")).isFalse();
        assertThat(Requisites.bik("044525225")).isTrue();
        assertThat(Requisites.account("40702810938000000001", "044525225")).isTrue();
        assertThat(Requisites.account("40702810938000000002", "044525225")).isFalse();
        assertThat(Requisites.account("30101810400000000225", "044525225")).isTrue();
        assertThat(Requisites.account("30101810000000000225", "044525225")).isFalse();
    }
}
