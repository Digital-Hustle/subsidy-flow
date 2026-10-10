package ru.subsidy.llm.api;

import java.time.Instant;

/**
 * Статус модели для бейджа оператора (FR-46).
 *
 * @param provider    провайдер
 * @param model       текстовая модель
 * @param visionModel vision-модель
 * @param reachable   отвечает ли провайдер
 * @param checkedAt   когда проверяли
 * @param error       причина недоступности
 */
public record LlmStatus(String provider, String model, String visionModel, boolean reachable, Instant checkedAt,
                        String error) {
}
