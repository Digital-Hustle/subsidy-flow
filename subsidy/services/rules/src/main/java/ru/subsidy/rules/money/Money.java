package ru.subsidy.rules.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Деньги: BigDecimal, scale 2, HALF_UP, сравнение с допуском 0.01 (ADR-007). Округление — только здесь.
 */
public final class Money {

    /** Копейки. */
    public static final int SCALE = 2;

    /** Допуск сравнения. */
    public static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static final Pattern NOISE = Pattern.compile("[^0-9,.\\-]");
    private static final String DOT = ".";
    private static final int GROUP = 3;

    private Money() {
    }

    /**
     * Разбирает строку суммы («1500000.00», «1 500 000,00», «1500000»).
     *
     * @param raw строка
     * @return сумма scale 2 или null
     */
    public static BigDecimal parse(String raw) {
        BigDecimal result = null;
        if (raw != null && !raw.isBlank()) {
            String text = NOISE.matcher(raw.replace('\u00a0', ' ')).replaceAll("");
            int sep = Math.max(text.lastIndexOf(','), text.lastIndexOf('.'));
            if (sep >= 0 && text.length() - sep - 1 <= SCALE) {
                text = text.substring(0, sep).replaceAll("[,.]", "") + DOT + text.substring(sep + 1);
            } else {
                text = text.replace(",", "").replace(DOT, "");
            }
            result = decimal(text);
        }
        return result;
    }

    private static BigDecimal decimal(String text) {
        BigDecimal result = null;
        if (!text.isEmpty() && !DOT.equals(text) && !"-".equals(text)) {
            try {
                result = round(new BigDecimal(text));
            } catch (NumberFormatException broken) {
                result = null;
            }
        }
        return result;
    }

    /**
     * Округляет до копеек.
     *
     * @param value значение
     * @return scale 2, HALF_UP
     */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Равны ли суммы с допуском.
     *
     * @param left первая
     * @param right вторая
     * @return |left − right| ≤ 0.01
     */
    public static boolean same(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(TOLERANCE) <= 0;
    }

    /**
     * Больше ли первая сумма второй сверх допуска.
     *
     * @param left первая
     * @param right вторая
     * @return left − right &gt; 0.01
     */
    public static boolean greater(BigDecimal left, BigDecimal right) {
        return left.subtract(right).compareTo(TOLERANCE) > 0;
    }

    /**
     * Текст для специалиста: «1 500 000,00».
     *
     * @param value сумма
     * @return строка
     */
    public static String format(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        format.setGroupingSize(GROUP);
        return format.format(round(value));
    }

    /**
     * Строка для JSON: «1500000.00».
     *
     * @param value сумма или null
     * @return строка или null
     */
    public static String plain(BigDecimal value) {
        return value == null ? null : round(value).toPlainString();
    }
}
