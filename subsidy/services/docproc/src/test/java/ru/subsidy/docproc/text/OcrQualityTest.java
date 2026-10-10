package ru.subsidy.docproc.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Маппинг метрик качества из ответа ocr в PageQuality (B2-3).
 */
class OcrQualityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void qualityMappedFromOcrPage() {
        ObjectNode page = JSON.createObjectNode();
        page.put("textLayer", false).put("meanConf", 0.62);
        ObjectNode quality = page.putObject("quality");
        quality.put("blur", 55.5).put("brightness", 0.31).put("glare", 0.12).put("cutOff", true);
        PageQuality metrics = OcrClient.qualityOf(page);
        assertThat(metrics.blur()).isEqualTo(55.5);
        assertThat(metrics.brightness()).isEqualTo(0.31);
        assertThat(metrics.glare()).isEqualTo(0.12);
        assertThat(metrics.cutOff()).isTrue();
        assertThat(metrics.meanConf()).isEqualTo(0.62);
    }

    @Test
    void textLayerHasNoQualityMetrics() {
        ObjectNode page = JSON.createObjectNode().put("textLayer", true).put("meanConf", 1.0);
        assertThat(OcrClient.qualityOf(page)).isNull();
    }

    @Test
    void noQualityNoMetrics() {
        ObjectNode page = JSON.createObjectNode().put("textLayer", false);
        assertThat(OcrClient.qualityOf(page)).isNull();
    }
}
