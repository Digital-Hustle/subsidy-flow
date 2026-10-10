package ru.subsidy.rules.messaging.consumer;

import java.io.IOException;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.event.Topics;
import ru.subsidy.rules.projection.ProjectionService;

/**
 * Слушает package.package.v1, package.file.v1 и docproc.document.v1 и обновляет проекцию.
 */
@Component
public class EventsConsumer {

    private final ProjectionService projection;
    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param projection проекция
     * @param json Jackson
     */
    public EventsConsumer(ProjectionService projection, ObjectMapper json) {
        this.projection = projection;
        this.json = json;
    }

    /**
     * Событие.
     *
     * @param message конверт
     * @throws IOException битый JSON
     */
    @KafkaListener(topics = {Topics.PACKAGE_PACKAGE_V1, Topics.PACKAGE_FILE_V1, Topics.DOCPROC_DOCUMENT_V1},
            groupId = "rules")
    public void onEvent(String message) throws IOException {
        JsonNode envelope = json.readTree(message);
        projection.handle(UUID.fromString(envelope.path("eventId").asText()), envelope.path("type").asText(),
                envelope.path("payload"));
    }
}
