package ru.subsidy.package_.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import ru.subsidy.package_.pkg.ApplicationData;

/**
 * Полная замена заявки с оптимистической блокировкой.
 *
 * @param expectedRevision версия, прочитанная клиентом
 * @param application новая заявка
 */
public record UpdateApplicationRq(@NotNull @Min(0) Integer expectedRevision, @NotNull @Valid ApplicationData application) {
}
