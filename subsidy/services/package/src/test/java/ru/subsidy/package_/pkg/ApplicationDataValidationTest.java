package ru.subsidy.package_.pkg;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;

class ApplicationDataValidationTest {

    private static ApplicationData application(String amount, ApplicationData.Property property) {
        return new ApplicationData("7707083893", "ООО Тест", "1027700132195",
                new ApplicationData.Address("Москва", "LEGAL"), "Проект", new BigDecimal(amount),
                LocalDate.of(2026, 10, 10), new ApplicationData.Signatory("Подписант", "Директор", "Устав"), property);
    }

    @Test
    void conditionsBelongToApplication_andKeysAndValuesAreValidated() {
        ApplicationData base = application("1.00", null);
        var valid = new ApplicationData(base.orgInn(), base.orgName(), base.ogrn(), base.address(), base.project(),
                base.requestedAmount(), base.applicationDate(), base.signatory(), null, Map.of("needsCertificate", true));
        var invalid = new ApplicationData(base.orgInn(), base.orgName(), base.ogrn(), base.address(), base.project(),
                base.requestedAmount(), base.applicationDate(), base.signatory(), null, Map.of("invalid.key", false));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(valid)).isEmpty();
            assertThat(factory.getValidator().validate(invalid)).isNotEmpty();
        }
    }

    @Test
    void propertyIsOptionalButItsFieldsAreValidatedWhenPresent() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(application("0.01", null))).isEmpty();
            assertThat(factory.getValidator().validate(application("100.00",
                    new ApplicationData.Property("bad", "", "LEASE")))).hasSize(2);
        }
    }

    @Test
    void soleProprietorInnAndOgrnipAreAccepted() {
        ApplicationData soleTrader = new ApplicationData("500100732259", "ИП Тестов", "304500116000157",
                new ApplicationData.Address("Москва", "LEGAL"), "Проект", new BigDecimal("100.00"),
                LocalDate.of(2026, 10, 10), new ApplicationData.Signatory("Тестов", "ИП", "ОГРНИП"), null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(soleTrader)).isEmpty();
            assertThat(factory.getValidator().validate(new ApplicationData("12345678901", "Х", "1027700132195",
                    soleTrader.address(), "П", soleTrader.requestedAmount(), soleTrader.applicationDate(),
                    soleTrader.signatory(), null))).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "0.001", "100000000000000000.00"})
    void invalidAmountsFailWithoutRounding(String amount) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(application(amount, null))).isNotEmpty();
        }
    }
}
