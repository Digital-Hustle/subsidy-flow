package ru.subsidy.package_.staff;

/**
 * Код отправлен.
 *
 * @param sentTo    маска адреса
 * @param expiresAt код действует до, Unix ms
 * @param resendAt  повторно не раньше, Unix ms
 * @param demoCode  код — только на демо-стенде (письма там может не быть), иначе null
 */
public record CodeSent(String sentTo, long expiresAt, long resendAt, String demoCode) {
}
