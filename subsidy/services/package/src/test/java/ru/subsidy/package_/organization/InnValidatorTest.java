package ru.subsidy.package_.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InnValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"7707083893", "7736050003", "500100732259"})
    void validInn_passes(String inn) {
        assertThat(InnValidator.isValid(inn)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"7707083894", "500100732250", "770708389", "77070838931", "77070838a3", " 7707083893"})
    void invalidInn_rejected(String inn) {
        assertThat(InnValidator.isValid(inn)).isFalse();
    }
}
