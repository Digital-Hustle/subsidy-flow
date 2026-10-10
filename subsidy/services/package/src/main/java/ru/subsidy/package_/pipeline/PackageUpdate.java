package ru.subsidy.package_.pipeline;

import java.util.Map;
import java.util.UUID;

/**
 * Изменение пакета, которое надо показать по SSE.
 *
 * @param packageId пакет
 * @param event имя SSE-события
 * @param data готовый к показу payload
 */
public record PackageUpdate(UUID packageId, String event, Map<String, Object> data) {
}
