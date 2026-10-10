package ru.subsidy.assistant.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class NumberValidatorTest {

    private final NumberValidator validator = new NumberValidator();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode tool(String text) throws Exception {
        return json.readTree(text);
    }

    @Test
    void formats_normalized() throws Exception {
        JsonNode data = tool("{\"amount\":\"1500000.00\",\"date\":\"2026-03-01\",\"failed\":12,\"rate\":0.5}");
        Validation result = validator.validate(
                "Запрошено 1 500 000,00 руб. по счёту от 01.03.2026, ошибок 12, ставка 0,50.", "", List.of(data));
        assertThat(result.status()).isEqualTo(Validation.OK);
        assertThat(result.unknown()).isEmpty();
    }

    @Test
    void inventedNumber_mismatch() throws Exception {
        JsonNode data = tool("{\"amount\":\"1500000.00\"}");
        Validation result = validator.validate("Сумма 1 500 000 руб., к выплате 750 000 руб. до 2026-12-31.", "",
                List.of(data));
        assertThat(result.status()).isEqualTo(Validation.MISMATCH);
        assertThat(result.unknown()).containsExactly("2026-12-31", "750000");
    }

    @Test
    void smallIntegers_andQuestionNumbers_allowed() {
        Validation result = validator.validate("1. Нет договора. 2. Срок выписки 30 дней.", "срок 30 дней?", List.of());
        assertThat(result.status()).isEqualTo(Validation.OK);
    }

    @Test
    void tokens_dateAndGroups() {
        assertThat(NumberValidator.tokens("12 345,60 и 31.12.2025 и 7"))
                .containsExactly("date:2025-12-31", "12345.6", "7");
    }
}
