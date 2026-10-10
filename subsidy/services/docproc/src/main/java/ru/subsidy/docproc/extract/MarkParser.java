package ru.subsidy.docproc.extract;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Нормализация ответа модели о визуальных признаках (B2-2): невалидное состояние → UNDETERMINED без уверенности.
 * Криптографическую действительность электронной подписи признак не заявляет.
 */
public final class MarkParser {

    /** Источник — изображение страницы (признаки определяются только по картинке). */
    public static final String IMAGE = "IMAGE";

    private static final String SIGNATURE = "signature";
    private static final String STAMP = "stamp";
    private static final String STATUS = "status";
    private static final String CONFIDENCE = "confidence";
    private static final int SCALE = 3;

    private MarkParser() {
    }

    /**
     * Разбирает ответ модели по изображениям страниц.
     *
     * @param marks узел {signature, stamp} (может отсутствовать)
     * @return признаки; отсутствующий или неизвестный признак → UNDETERMINED
     */
    public static DocumentMarks parse(JsonNode marks) {
        return new DocumentMarks(mark(marks.path(SIGNATURE)), mark(marks.path(STAMP)));
    }

    /**
     * Признаки не определялись (нет изображения страницы, вид без подписи, модель недоступна).
     *
     * @return оба признака UNDETERMINED без источника
     */
    public static DocumentMarks undetermined() {
        MarkState none = new MarkState(MarkState.UNDETERMINED, null, null);
        return new DocumentMarks(none, none);
    }

    private static MarkState mark(JsonNode node) {
        String status = switch (node.path(STATUS).asText("")) {
            case MarkState.DETECTED -> MarkState.DETECTED;
            case MarkState.NOT_DETECTED -> MarkState.NOT_DETECTED;
            case null, default -> MarkState.UNDETERMINED;
        };
        BigDecimal confidence = !MarkState.UNDETERMINED.equals(status) && node.path(CONFIDENCE).isNumber()
                ? BigDecimal.valueOf(Math.clamp(node.path(CONFIDENCE).asDouble(), 0, 1)).setScale(SCALE, RoundingMode.HALF_UP)
                : null;
        return new MarkState(status, IMAGE, confidence);
    }
}
