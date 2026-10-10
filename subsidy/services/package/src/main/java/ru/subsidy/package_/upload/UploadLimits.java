package ru.subsidy.package_.upload;

/**
 * Лимиты приёма (UPLOAD_MAX_MB, UPLOAD_MAX_PAGES).
 *
 * @param maxBytes максимальный размер файла в байтах
 * @param maxPages максимум страниц
 */
public record UploadLimits(long maxBytes, int maxPages) {
}
