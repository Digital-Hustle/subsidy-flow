package ru.subsidy.docproc.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ru.subsidy.docproc.text.PageQuality;
import ru.subsidy.docproc.text.PageText;

/**
 * Пороги качества сканов и причины нечитаемости/частичной читаемости (B2-3).
 * ocr измеряет (blur, brightness, glare, cutOff, meanConf); здесь — решения.
 * Скан целиком нечитаем → UNREADABLE с объяснимой причиной; частично читаем → NEEDS_REVIEW
 * (документ идёт на ручную проверку, поля не выдумываются).
 */
@Component
public class PageQualityAssessor {

    private static final double BLUR_MIN = 100.0;
    private static final double BRIGHTNESS_MIN = 0.20;
    private static final double GLARE_MAX = 0.30;
    private static final double MEAN_CONF_PARTIAL = 0.55;

    /**
     * Причина нечитаемости всего файла («размыт», «темно», «пересвет», «обрезан», «нет текста») или null,
     * если файл читаем. Учитываются только страницы со сканом (OCR); текстовый слой и office не мешают.
     *
     * @param pages страницы файла
     * @return причина по-русски или null
     */
    public String unreadableReason(List<PageText> pages) {
        List<PageText> ocrPages = ocrPages(pages);
        if (ocrPages.isEmpty() || ocrPages.stream().anyMatch(PageText::hasText)) {
            return null;
        }
        return cause(ocrPages);
    }

    /**
     * Частичная читаемость: текст есть, но у части сканов качество низкое (слабый OCR, размытие, темно, блики, обрезка).
     *
     * @param pages страницы файла
     * @return true — документ частично читаем, нужна ручная проверка
     */
    public boolean partial(List<PageText> pages) {
        return ocrPages(pages).stream().anyMatch(PageQualityAssessor::isPartialPage);
    }

    private static boolean isPartialPage(PageText page) {
        PageQuality quality = page.quality();
        if (quality == null) {
            return false;
        }
        return PageText.OCR_WEAK.equals(page.source()) || isLowConfidence(quality) || isDegraded(quality);
    }

    private static boolean isLowConfidence(PageQuality quality) {
        return quality.meanConf() != null && quality.meanConf() < MEAN_CONF_PARTIAL;
    }

    private static boolean isDegraded(PageQuality quality) {
        return quality.blur() != null && quality.blur() < BLUR_MIN
                || quality.brightness() != null && quality.brightness() < BRIGHTNESS_MIN
                || quality.glare() != null && quality.glare() > GLARE_MAX
                || Boolean.TRUE.equals(quality.cutOff());
    }

    private static List<PageText> ocrPages(List<PageText> pages) {
        return pages.stream()
                .filter(page -> PageText.OCR.equals(page.source()) || PageText.OCR_WEAK.equals(page.source())
                        || PageText.NONE.equals(page.source()))
                .toList();
    }

    private static String cause(List<PageText> ocrPages) {
        List<String> causes = new ArrayList<>();
        for (PageText page : ocrPages) {
            PageQuality quality = page.quality();
            if (quality == null) {
                continue;
            }
            addIf(causes, quality.blur() != null && quality.blur() < BLUR_MIN, "размыт");
            addIf(causes, quality.brightness() != null && quality.brightness() < BRIGHTNESS_MIN, "темно");
            addIf(causes, quality.glare() != null && quality.glare() > GLARE_MAX, "пересвет");
            addIf(causes, Boolean.TRUE.equals(quality.cutOff()), "обрезан");
        }
        return causes.isEmpty() ? "нет текста" : "нечитаемо: " + String.join(", ", causes);
    }

    private static void addIf(List<String> causes, boolean condition, String cause) {
        if (condition && !causes.contains(cause)) {
            causes.add(cause);
        }
    }
}
