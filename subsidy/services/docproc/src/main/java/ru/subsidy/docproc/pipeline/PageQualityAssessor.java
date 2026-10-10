package ru.subsidy.docproc.pipeline;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import ru.subsidy.docproc.text.PageQuality;
import ru.subsidy.docproc.text.PageText;

/**
 * Пороги качества сканов и причины нечитаемости/частичной читаемости (B2-3, FR-26).
 * ocr измеряет (blur, brightness, glare, cutOff, meanConf); здесь — решения.
 * Скан без текста → UNREADABLE с кодом причины из API.md (IMAGE_TOO_DARK, IMAGE_GLARE, IMAGE_BLURRY,
 * IMAGE_CUT_OFF, иначе TEXT_NOT_FOUND). Текст распознан плохо или упирается в край кадра → документ частично
 * читаем, идёт на ручную проверку (PARTIAL_SCAN); поля не выдумываются. Метрики изображения без признаков плохого
 * распознавания на ручную проверку не отправляют: если OCR уверенно прочитал текст, размытие/яркость не мешают.
 */
@Component
public class PageQualityAssessor {

    /** Скан размыт. */
    public static final String IMAGE_BLURRY = "IMAGE_BLURRY";
    /** Скан слишком тёмный. */
    public static final String IMAGE_TOO_DARK = "IMAGE_TOO_DARK";
    /** Пересвет / блики. */
    public static final String IMAGE_GLARE = "IMAGE_GLARE";
    /** Документ обрезан. */
    public static final String IMAGE_CUT_OFF = "IMAGE_CUT_OFF";
    /** Текста нет, явной причины по метрикам нет. */
    public static final String TEXT_NOT_FOUND = "TEXT_NOT_FOUND";

    private static final double BLUR_MIN = 100.0;
    private static final double BRIGHTNESS_MIN = 0.20;
    private static final double GLARE_MAX = 0.30;
    private static final double MEAN_CONF_PARTIAL = 0.55;

    /**
     * Код причины нечитаемости файла или null, если среди страниц нет сканов или на скане есть текст.
     *
     * @param pages страницы файла
     * @return код причины (API.md §Причины) или null
     */
    public String unreadableReason(List<PageText> pages) {
        List<PageText> scans = scanPages(pages);
        if (scans.isEmpty() || scans.stream().anyMatch(PageText::hasText)) {
            return null;
        }
        return cause(scans);
    }

    /**
     * Частичная читаемость: на скан-странице текст распознан неуверенно или упирается в край кадра.
     *
     * @param pages страницы документа
     * @return true — нужна ручная проверка
     */
    public boolean partial(List<PageText> pages) {
        return scanPages(pages).stream().anyMatch(PageQualityAssessor::isPartialPage);
    }

    private static boolean isPartialPage(PageText page) {
        if (PageText.OCR_WEAK.equals(page.source())) {
            return true;
        }
        PageQuality quality = page.quality();
        if (quality == null || !page.hasText()) {
            return false;
        }
        boolean lowConfidence = quality.meanConf() != null && quality.meanConf() < MEAN_CONF_PARTIAL;
        return lowConfidence || Boolean.TRUE.equals(quality.cutOff());
    }

    private static List<PageText> scanPages(List<PageText> pages) {
        return pages.stream()
                .filter(page -> PageText.OCR.equals(page.source()) || PageText.OCR_WEAK.equals(page.source())
                        || PageText.NONE.equals(page.source()))
                .toList();
    }

    private static String cause(List<PageText> scans) {
        List<PageQuality> metrics = scans.stream().map(PageText::quality).filter(Objects::nonNull).toList();
        String code = TEXT_NOT_FOUND;
        if (metrics.stream().anyMatch(metric -> metric.brightness() != null && metric.brightness() < BRIGHTNESS_MIN)) {
            code = IMAGE_TOO_DARK;
        } else if (metrics.stream().anyMatch(metric -> metric.glare() != null && metric.glare() > GLARE_MAX)) {
            code = IMAGE_GLARE;
        } else if (metrics.stream().anyMatch(metric -> metric.blur() != null && metric.blur() < BLUR_MIN)) {
            code = IMAGE_BLURRY;
        } else if (metrics.stream().anyMatch(metric -> Boolean.TRUE.equals(metric.cutOff()))) {
            code = IMAGE_CUT_OFF;
        }
        return code;
    }
}
