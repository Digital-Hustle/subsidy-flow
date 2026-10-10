package ru.subsidy.rules.api.dto;

import java.util.UUID;

/**
 * Принято, перепроверка запланирована.
 *
 * @param packageId пакет
 * @param status RECHECK_SCHEDULED
 */
public record AcceptedRs(UUID packageId, String status) {
}
