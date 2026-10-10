package ru.subsidy.docproc.queue;

import java.util.UUID;

/**
 * Задание разбора файла.
 *
 * @param id задание
 * @param fileId файл
 * @param packageId пакет
 * @param version версия файла
 * @param mime тип содержимого
 * @param stage текущая стадия
 * @param attempts потраченные попытки
 */
public record Job(UUID id, UUID fileId, UUID packageId, int version, String mime, String stage, int attempts) {
}
