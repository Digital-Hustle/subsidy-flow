package ru.subsidy.package_.messaging.consumer;

import java.io.IOException;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.event.Topics;
import ru.subsidy.package_.pipeline.PipelineEventsService;
import ru.subsidy.package_.stream.StreamHub;

/**
 * События docproc и rules → стадии и статус пакета → SSE (FR-62).
 */
@Component
public class PipelineConsumer {

    private final PipelineEventsService service;
    private final StreamHub hub;
    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param service обработка событий
     * @param hub SSE
     * @param json Jackson
     */
    public PipelineConsumer(PipelineEventsService service, StreamHub hub, ObjectMapper json) {
        this.service = service;
        this.hub = hub;
        this.json = json;
    }

    /**
     * Topic docproc.document.v1 и rules.package.v1.
     *
     * @param message конверт события
     * @throws IOException некорректный JSON
     */
    @KafkaListener(topics = {Topics.DOCPROC_DOCUMENT_V1, Topics.RULES_PACKAGE_V1}, groupId = "package")
    public void onEvent(String message) throws IOException {
        JsonNode envelope = json.readTree(message);
        String eventId = envelope.path("eventId").asText();
        service.handle(UUID.fromString(eventId), envelope.path("type").asText(), envelope.path("payload"))
                .ifPresent(update -> hub.publish(eventId, update));
    }
}
