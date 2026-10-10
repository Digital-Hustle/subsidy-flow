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
    void detectedWithConfidence() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.readTree("""
                {"signature": {"status": "DETECTED", "confidence": 0.92, "basis": "росчерк"},
                 "stamp": {"status": "NOT_DETECTED", "confidence": 0.8, "basis": "печати нет"}}
                """), false);
        assertThat(marks.signature().status()).isEqualTo(MarkState.DETECTED);
        assertThat(marks.signature().source()).isEqualTo(MarkParser.TEXT);
        assertThat(marks.signature().confidence()).isEqualByComparingTo("0.920");
        assertThat(marks.stamp().status()).isEqualTo(MarkState.NOT_DETECTED);
    }

    @Test
    void visionSourceWhenImagesGiven() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.readTree("""
                {"signature": {"status": "DETECTED", "confidence": 0.9},
                 "stamp": {"status": "DETECTED", "confidence": 0.7}}
                """), true);
        assertThat(marks.signature().source()).isEqualTo(MarkParser.TEXT_AND_IMAGE);
        assertThat(marks.stamp().source()).isEqualTo(MarkParser.TEXT_AND_IMAGE);
    }

    @Test
    void unknownStatusIsUndeterminedWithoutConfidence() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.readTree("""
                {"signature": {"status": "MAYBE", "confidence": 0.99},
                 "stamp": {"status": "UNDETERMINED", "confidence": 0.5}}
                """), false);
        assertThat(marks.signature().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.signature().confidence()).isNull();
        assertThat(marks.stamp().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.stamp().confidence()).isNull();
    }

    @Test
    void missingMarksAreUndetermined() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.createObjectNode(), false);
        assertThat(marks.signature().status()).isEqualTo(MarkState.UNDETERMINED);
        assertThat(marks.stamp().status()).isEqualTo(MarkState.UNDETERMINED);
    }

    @Test
    void confidenceClampedToRange() throws Exception {
        DocumentMarks marks = MarkParser.parse(JSON.readTree("""
                {"signature": {"status": "DETECTED", "confidence": 1.7},
                 "stamp": {"status": "DETECTED", "confidence": -0.2}}
                """), false);
        assertThat(marks.signature().confidence()).isEqualByComparingTo("1.000");
        assertThat(marks.stamp().confidence()).isEqualByComparingTo("0.000");
    }
}
