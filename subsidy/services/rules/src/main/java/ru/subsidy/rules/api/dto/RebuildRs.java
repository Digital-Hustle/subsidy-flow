package ru.subsidy.rules.api.dto;

import java.util.UUID;

/**
 * Итог пересборки проекции пакета.
 *
 * @param packageId пакет
 * @param documents документов в docproc
 * @param updated обновлено в проекции
 * @param removed снято из проекции (в docproc их нет)
 * @param status RECHECK_SCHEDULED
 */
public record RebuildRs(UUID packageId, int documents, int updated, int removed, String status) {
}
