package ru.subsidy.docproc.normalize;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Дата из текста → LocalDate: «01.02.2026», «1 февраля 2026 г.», ««01» февраля 2026», «2026-02-01».
 */
public final class DateNormalizer {

    private static final Pattern NUMERIC = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})");
    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern WORDS = Pattern.compile("(\\d{1,2})\\s+([а-яё]+)\\s+(\\d{4})");
    private static final String MARCH = "март";
    private static final int YEAR = 3;
    private static final int CENTURY = 2000;
    private static final int TWO_DIGITS = 100;
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("январ", 1), Map.entry("феврал", 2), Map.entry(MARCH, 3), Map.entry("апрел", 4),
            Map.entry("ма", 5), Map.entry("июн", 6), Map.entry("июл", 7), Map.entry("август", 8),
            Map.entry("сентябр", 9), Map.entry("октябр", 10), Map.entry("ноябр", 11), Map.entry("декабр", 12));

    private DateNormalizer() {
    }

    /**
     * Разбирает дату.
     *
     * @param raw текст
     * @return дата или null
     */
    public static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String text = raw.toLowerCase(Locale.ROOT).replaceAll("[«»\"']", "").replace('\u00a0', ' ');
        LocalDate date = null;
        Matcher iso = ISO.matcher(text);
        Matcher num = NUMERIC.matcher(text);
        Matcher words = WORDS.matcher(text);
        if (iso.find()) {
            date = of(iso.group(1), iso.group(2), iso.group(YEAR));
        } else if (num.find()) {
            date = of(num.group(YEAR), num.group(2), num.group(1));
        } else if (words.find()) {
            Integer month = month(words.group(2));
            date = month == null ? null : of(words.group(YEAR), month.toString(), words.group(1));
        }
        return date;
    }

    private static Integer month(String word) {
        if (word.startsWith("мар")) {
            return MONTHS.get(MARCH);
        }
        return MONTHS.entrySet().stream().filter(entry -> word.startsWith(entry.getKey()))
                .max((left, right) -> Integer.compare(left.getKey().length(), right.getKey().length()))
                .map(Map.Entry::getValue).orElse(null);
    }

    private static LocalDate of(String year, String month, String day) {
        int fullYear = Integer.parseInt(year);
        if (fullYear < TWO_DIGITS) {
            fullYear += CENTURY;
        }
        try {
            return LocalDate.of(fullYear, Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException broken) {
            return null;
        }
    }
}
