package ru.subsidy.docproc.pipeline;

import java.util.UUID;

import ru.subsidy.docproc.classify.Segment;

/**
 * Документ файла: id и сегмент.
 *
 * @param documentId документ
 * @param segment страницы и вид
 */
public record DocSegment(UUID documentId, Segment segment) {
}
