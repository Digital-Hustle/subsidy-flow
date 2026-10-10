package ru.subsidy.docproc.extract;

import java.math.BigDecimal;

/**
 * Признак визуальной подписи или печати на документе (B2-2).
 * Определяет модель по изображению страницы отдельным вызовом; код только нормализует состояние.
 * Признак не заявляет криптографическую действительность электронной подписи — только видимое наличие.
 *
 * @param status DETECTED / NOT_DETECTED / UNDETERMINED
 * @param source IMAGE — по изображению страницы; null — не определялось
 * @param confidence уверенность модели 0..1 (null — не указана)
 */
public record MarkState(String status, String source, BigDecimal confidence) {

    /** Признак обнаружен. */
    public static final String DETECTED = "DETECTED";
    /** Признак не обнаружен. */
    public static final String NOT_DETECTED = "NOT_DETECTED";
    /** Не удалось определить. */
    public static final String UNDETERMINED = "UNDETERMINED";
}
