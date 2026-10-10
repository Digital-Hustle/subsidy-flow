package ru.subsidy.docproc.extract;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Нормализация признаков подписи/печати от модели (B2-2): невалидное состояние → UNDETERMINED,
 * источник — по факту запроса (картинки страниц подавались или нет). Криптографическую действительность
 * электронной подписи признак не заявляет.
 */
public final class MarkParser {

    /** Источник — текстовый слой. */
    public static final String TEXT = "TEXT";
    /** Источник — изображение страницы. */
    public static final String IMAGE = "IMAGE";
    /** Источник — текст и изображение. */
    public static final String TEXT_AND_IMAGE = "TEXT_AND_IMAGE";

    private static final String SIGNATURE = "signature";
    private static final String STAMP = "stamp";
    private static final String STATUS = "status";
    private static final String CONFIDENCE = "confidence";
    private static final int SCALE = 3;

    private MarkParser() {
    }

    /**
     * Разбирает marks из ответа модели.
     *
     * @param marks узел marks (может отсутствовать)
     * @param vision подавались ли модели изображения страниц
     * @return признаки; отсутствующий признак → UNDETERMINED
     */
    public static DocumentMarks parse(JsonNode marks, boolean vision) {
        String source = vision ? TEXT_AND_IMAGE : TEXT;
        return new DocumentMarks(mark(marks.path(SIGNATURE), source), mark(marks.path(STAMP), source));
    }

    private static MarkState mark(JsonNode node, String source) {
        String status = switch (node.path(STATUS).asText("")) {
            case MarkState.DETECTED -> MarkState.DETECTED;
            case MarkState.NOT_DETECTED -> MarkState.NOT_DETECTED;
            case null, default -> MarkState.UNDETERMINED;
        };
        BigDecimal confidence = node.path(CONFIDENCE).isNumber()
                ? BigDecimal.valueOf(Math.clamp(node.path(CONFIDENCE).asDouble(), 0, 1))
                        .setScale(SCALE, RoundingMode.HALF_UP)
                : null;
        if (MarkState.UNDETERMINED.equals(status)) {
            confidence = null;
        }
        return new MarkState(status, source, confidence);
    }
}
