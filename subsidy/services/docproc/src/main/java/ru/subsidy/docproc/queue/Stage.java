package ru.subsidy.docproc.queue;

import java.util.List;

/**
 * Стадии файла и документа (docs/API.md «Стадии»).
 */
public final class Stage {

    /** В очереди. */
    public static final String QUEUED = "QUEUED";
    /** Читаем текст. */
    public static final String EXTRACTING_TEXT = "EXTRACTING_TEXT";
    /** Сегментация и вид. */
    public static final String CLASSIFYING = "CLASSIFYING";
    /** Извлечение полей. */
    public static final String EXTRACTING_FIELDS = "EXTRACTING_FIELDS";
    /** Проверка реквизитов. */
    public static final String VALIDATING = "VALIDATING";
    /** Разобран. */
    public static final String PARSED = "PARSED";
    /** Разобран, есть что проверить. */
    public static final String NEEDS_REVIEW = "NEEDS_REVIEW";
    /** Не прочитать. */
    public static final String UNREADABLE = "UNREADABLE";
    /** Модель недоступна. */
    public static final String WAITING_MODEL = "WAITING_MODEL";
    /** Не удалось разобрать. */
    public static final String FAILED = "FAILED";
    /** Файл удалён из пакета. */
    public static final String REMOVED = "REMOVED";

    /** Финальные стадии: задание больше не берётся. */
    public static final List<String> FINAL = List.of(PARSED, NEEDS_REVIEW, UNREADABLE, FAILED, REMOVED);

    private Stage() {
    }
}
