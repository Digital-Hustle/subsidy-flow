package ru.subsidy.docproc.extract;

/**
 * Визуальные признаки документа (B2-2): подпись и печать.
 *
 * @param signature признак подписи
 * @param stamp признак печати
 */
public record DocumentMarks(MarkState signature, MarkState stamp) {
}
