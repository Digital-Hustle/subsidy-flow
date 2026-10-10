package ru.subsidy.docproc.normalize;

/**
 * Проверка значения поля.
 *
 * @param code код (CHECKSUM_FAILED, FORMAT_INVALID, LOW_CONFIDENCE)
 * @param status OK или FAILED
 * @param message текст для специалиста
 */
public record FieldCheck(String code, String status, String message) {

    /** Проверка пройдена. */
    public static final String OK = "OK";
    /** Проверка не пройдена. */
    public static final String FAILED = "FAILED";
}
