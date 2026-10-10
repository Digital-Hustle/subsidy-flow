package ru.subsidy.docproc.api.dto;

import java.util.SortedSet;

/**
 * Ответ ping.
 *
 * @param service сервис
 * @param userId пользователь
 * @param roles роли
 */
public record PingRs(String service, String userId, SortedSet<String> roles) {
}
