package ru.subsidy.docproc.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class OcrSourceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ObjectNode page(boolean textLayer, double meanConf) {
        return JSON.createObjectNode().put("textLayer", textLayer).put("meanConf", meanConf);
    }

    @Test
    void lowConfidenceScanIsWeakAndNeedsImage() {
        String source = OcrClient.sourceOf(page(false, 0.41), "Счёт № 12", 0.6);
        assertThat(source).isEqualTo(PageText.OCR_WEAK);
        PageText weak = new PageText(1, "Счёт № 12", source);
        assertThat(weak.hasText()).isTrue();
        assertThat(weak.needsImage()).isTrue();
    }

    @Test
    void confidentScanAndTextLayerAreNotWeak() {
        assertThat(OcrClient.sourceOf(page(false, 0.85), "Счёт", 0.6)).isEqualTo(PageText.OCR);
        assertThat(OcrClient.sourceOf(page(true, 0.1), "Счёт", 0.6)).isEqualTo(PageText.OCR);
        assertThat(new PageText(1, "Счёт", PageText.OCR).needsImage()).isFalse();
    }

    @Test
    void emptyTextIsNone() {
        assertThat(OcrClient.sourceOf(page(false, 0.9), "  ", 0.6)).isEqualTo(PageText.NONE);
        assertThat(new PageText(1, "", PageText.NONE).needsImage()).isTrue();
    }
}
