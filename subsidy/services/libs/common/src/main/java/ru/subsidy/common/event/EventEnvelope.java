package ru.subsidy.common.event;

import java.time.Instant;
import java.util.Map;

/**
 * Конверт события в Kafka (см. docs/ARCHITECTURE.md «Схема событий»).
 * Ключ сообщения — aggregateId (порядок в партиции).
 *
 * @param eventId     уникальный id (UUIDv7)
 * @param type        тип события (например PassDecided)
 * @param occurredAt  момент события
 * @param aggregateId id агрегата
 * @param traceparent trace context (W3C)
 * @param payload     данные события
 */
public record EventEnvelope(
        String eventId,
        String type,
        Instant occurredAt,
        String aggregateId,
        String traceparent,
        Map<String, Object> payload
) {
}
