package ru.subsidy.package_.staff;

import java.time.Instant;
import java.util.UUID;

/**
 * Сотрудник.
 *
 * @param id       идентификатор
 * @param name     имя
 * @param email    почта (нижний регистр)
 * @param role     роль
 * @param status   статус
 * @param demo     демо-сотрудник (заводится только на демо-стенде)
 * @param lastSeen последний вход или null
 */
public record StaffUser(UUID id, String name, String email, StaffRole role, StaffStatus status, boolean demo, Instant lastSeen) {

    /**
     * Копия с другой ролью.
     *
     * @param value роль
     * @return сотрудник
     */
    public StaffUser withRole(StaffRole value) {
        return new StaffUser(id, name, email, value, status, demo, lastSeen);
    }

    /**
     * Копия с другим статусом.
     *
     * @param value статус
     * @return сотрудник
     */
    public StaffUser withStatus(StaffStatus value) {
        return new StaffUser(id, name, email, role, value, demo, lastSeen);
    }
}
