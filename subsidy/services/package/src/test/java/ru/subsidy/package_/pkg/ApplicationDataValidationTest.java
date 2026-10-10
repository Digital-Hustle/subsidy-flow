package ru.subsidy.package_.pkg;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    void propertyIsOptionalButItsFieldsAreValidatedWhenPresent() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(application("0.01", null))).isEmpty();
            assertThat(factory.getValidator().validate(application("100.00",
                    new ApplicationData.Property("bad", "", "LEASE")))).hasSize(2);
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
