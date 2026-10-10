package ru.subsidy.docproc.classify;

import java.math.BigDecimal;

/**
 * Документ внутри файла по результату сегментации.
 *
 * @param fromPage первая страница
 * @param toPage последняя страница
 * @param type вид
 * @param confidence уверенность модели
 * @param evidence признак из текста
 */
public record Segment(int fromPage, int toPage, String type, BigDecimal confidence, String evidence) {
}
