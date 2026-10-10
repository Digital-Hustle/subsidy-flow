package ru.subsidy.docproc.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Признаки подписи и печати (B2-2): состояния, источник, уверенность; неопределённость не скрывается.
 */
class MarkParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void detectedWithConfidenceFromImage() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.readTree("""
                {"signature": {"status": "DETECTED", "confidence": 0.92, "basis": "росчерк"},
                 "stamp": {"status": "NOT_DETECTED", "confidence": 1.7, "basis": "печати нет"}}
                """));
        assertThat(marks.signature().status()).isEqualTo(MarkState.DETECTED);
        assertThat(marks.signature().source()).isEqualTo(MarkParser.IMAGE);
        assertThat(marks.signature().confidence()).isEqualByComparingTo("0.920");
        assertThat(marks.stamp().status()).isEqualTo(MarkState.NOT_DETECTED);
        assertThat(marks.stamp().confidence()).isEqualByComparingTo("1.000");
    }

    @Test
    void unknownOrMissingStatusIsUndeterminedWithoutConfidence() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.readTree("""
                {"signature": {"status": "MAYBE", "confidence": 0.99}}
                """));
        assertThat(marks.signature().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.signature().confidence()).isNull();
        assertThat(marks.stamp().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.stamp().confidence()).isNull();
    }

    @Test
    void notCheckedHasNoSource() {
        DocumentMarks marks = MarkParser.undetermined();
        assertThat(marks.signature().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.signature().source()).isNull();
        assertThat(marks.stamp().source()).isNull();
    }
}
