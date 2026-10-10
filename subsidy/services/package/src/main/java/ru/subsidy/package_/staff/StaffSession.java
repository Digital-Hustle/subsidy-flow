package ru.subsidy.package_.staff;

/**
 * Открытая сессия.
 *
 * @param accessToken      JWT для сервисов
 * @param refreshToken     одноразовый refresh (уходит в httpOnly-cookie)
 * @param expiresAt        access истекает, Unix ms
 * @param sessionExpiresAt сессия истекает, Unix ms
 * @param user             сотрудник
 */
public record StaffSession(String accessToken, String refreshToken, long expiresAt, long sessionExpiresAt, StaffUser user) {
}
