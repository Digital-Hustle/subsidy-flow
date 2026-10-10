package ru.subsidy.docproc.normalize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сумма из текста документа → BigDecimal scale 2: «1 234 567,89 руб.», «1.234.567-89», «1234567.89».
 */
public final class MoneyNormalizer {

    private static final int SCALE = 2;
    private static final Pattern KOPECKS = Pattern.compile("^(.*\\d)\\s*руб\\.?\\s*(\\d{1,2})\\s*коп.*$");
    private static final Pattern DASH_KOPECKS = Pattern.compile("^(\\d+)-(\\d{2})$");
    private static final Pattern NOISE = Pattern.compile("[^0-9,.\\-]");

    private static final String DOT = ".";
    private static final String SEPARATORS = "[,.]";

    private MoneyNormalizer() {
    }

    /**
     * Разбирает сумму.
     *
     * @param raw текст
     * @return сумма или null, если не похоже на число
     */
    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.toLowerCase(Locale.ROOT).replace('\u00a0', ' ').strip();
        Matcher kop = KOPECKS.matcher(text);
        if (kop.matches()) {
            text = kop.group(1) + "," + kop.group(2);
        }
        String digits = NOISE.matcher(text).replaceAll("");
        Matcher dash = DASH_KOPECKS.matcher(digits);
        if (dash.matches()) {
            digits = dash.group(1) + DOT + dash.group(2);
        }
        return toDecimal(digits.replaceAll("^[-,.]+|[-,.]+$", ""));
    }

    private static BigDecimal toDecimal(String digits) {
        String normalized = digits;
        int comma = normalized.lastIndexOf(',');
        int dot = normalized.lastIndexOf('.');
        int sep = Math.max(comma, dot);
        if (sep >= 0 && normalized.length() - sep - 1 <= SCALE) {
            String whole = normalized.substring(0, sep).replaceAll(SEPARATORS, "");
            normalized = whole + DOT + normalized.substring(sep + 1);
        } else {
            normalized = normalized.replaceAll(SEPARATORS, "");
        }
        if (normalized.isEmpty() || DOT.equals(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized).setScale(SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException broken) {
            return null;
        }
    }
}
