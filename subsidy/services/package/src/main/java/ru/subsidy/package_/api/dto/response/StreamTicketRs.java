package ru.subsidy.package_.api.dto.response;

/**
 * Одноразовый билет SSE.
 *
 * @param ticket билет
 * @param expiresInSeconds срок жизни, с
 */
public record StreamTicketRs(String ticket, long expiresInSeconds) {
}
