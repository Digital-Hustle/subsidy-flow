package ru.subsidy.docproc.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ru.subsidy.docproc.text.PageQuality;
import ru.subsidy.docproc.text.PageText;

/**
 * Пороги качества сканов (B2-3): нечитаемый — объяснимая причина; частично читаемый — на ручную проверку.
 */
class PageQualityAssessorTest {

    private final PageQualityAssessor assessor = new PageQualityAssessor();

    private static PageText scan(String text, PageQuality quality) {
        return new PageText(1, text, PageText.OCR, null, quality);
    }

    @Test
    void unreadable_blurHasCause() {
        PageText page = scan("", new PageQuality(40.0, 0.8, 0.0, false, 0.9));
        assertThat(assessor.unreadableReason(List.of(page))).isEqualTo(PageQualityAssessor.IMAGE_BLURRY);
    }

    @Test
    void unreadable_darkHasCause() {
        PageText page = scan("", new PageQuality(300.0, 0.10, 0.0, false, 0.9));
        assertThat(assessor.unreadableReason(List.of(page))).isEqualTo(PageQualityAssessor.IMAGE_TOO_DARK);
    }

    @Test
    void unreadable_cutOffHasCause() {
        PageText page = scan("", new PageQuality(300.0, 0.8, 0.0, true, 0.9));
        assertThat(assessor.unreadableReason(List.of(page))).isEqualTo(PageQualityAssessor.IMAGE_CUT_OFF);
    }

    @Test
    void unreadable_noMetricsGivesNoText() {
        PageText page = new PageText(1, "", PageText.NONE);
        assertThat(assessor.unreadableReason(List.of(page))).isEqualTo(PageQualityAssessor.TEXT_NOT_FOUND);
    }

    @Test
    void readableFile_hasNoUnreadableReason() {
        PageText page = scan("Заявление на предоставление субсидии", new PageQuality(300.0, 0.8, 0.0, false, 0.95));
        assertThat(assessor.unreadableReason(List.of(page))).isNull();
    }

    @Test
    void textLayerOnly_notScanned_notUnreadable() {
        PageText page = new PageText(1, "Заявление", PageText.TEXT_LAYER);
        assertThat(assessor.unreadableReason(List.of(page))).isNull();
        assertThat(assessor.partial(List.of(page))).isFalse();
    }

    @Test
    void partial_lowMeanConf() {
        PageText page = scan("Заявление", new PageQuality(300.0, 0.8, 0.0, false, 0.40));
        assertThat(assessor.partial(List.of(page))).isTrue();
    }

    @Test
    void partial_ocrWeakSource() {
        PageText page = new PageText(1, "Заявление", PageText.OCR_WEAK, null,
                new PageQuality(300.0, 0.8, 0.0, false, 0.95));
        assertThat(assessor.partial(List.of(page))).isTrue();
    }

    @Test
    void unreadable_glareHasCause() {
        PageText page = scan("", new PageQuality(300.0, 0.8, 0.5, false, 0.9));
        assertThat(assessor.unreadableReason(List.of(page))).isEqualTo(PageQualityAssessor.IMAGE_GLARE);
    }

    @Test
    void glareOrBlurWithConfidentOcr_notPartial() {
        PageText page = scan("Заявление", new PageQuality(40.0, 0.8, 0.5, false, 0.95));
        assertThat(assessor.partial(List.of(page))).isFalse();
    }

    @Test
    void partial_cutOffText() {
        PageText page = scan("Заявление", new PageQuality(300.0, 0.8, 0.0, true, 0.95));
        assertThat(assessor.partial(List.of(page))).isTrue();
    }

    @Test
    void goodScan_notPartial() {
        PageText page = scan("Заявление", new PageQuality(300.0, 0.8, 0.0, false, 0.95));
        assertThat(assessor.partial(List.of(page))).isFalse();
    }

    @Test
    void mixedPages_goodAndTextLayer_notPartial() {
        PageText good = scan("Текст", new PageQuality(300.0, 0.8, 0.0, false, 0.95));
        PageText layer = new PageText(2, "Текст", PageText.TEXT_LAYER);
        assertThat(assessor.partial(List.of(good, layer))).isFalse();
    }
}
