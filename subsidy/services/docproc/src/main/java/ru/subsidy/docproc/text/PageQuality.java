package ru.subsidy.docproc.text;

/**
 * Метрики качества страницы скана (ocr измеряет; пороги и решения — в docproc).
 *
 * @param blur дисперсия лапласиана; меньше — размытее (null — не измерялась)
 * @param brightness средняя яркость 0..1 (null — не измерялась)
 * @param glare доля пересвеченных пикселей 0..1 (null — не измерялась)
 * @param cutOff текст упирается в край кадра (false — не измерялась)
 * @param meanConf средняя уверенность распознавания слов 0..1 (null — не измерялась)
 */
public record PageQuality(Double blur, Double brightness, Double glare, Boolean cutOff, Double meanConf) {
}
