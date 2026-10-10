package ru.subsidy.docproc.messaging.consumer;

import java.io.IOException;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.event.Topics;
import ru.subsidy.docproc.queue.FileEventsService;

/**
 * Слушает package.file.v1 и ставит файлы в очередь разбора.
 */
@Component
public class FileEventsConsumer {

    private final FileEventsService service;
    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param service обработка
     * @param json Jackson
     */
    public FileEventsConsumer(FileEventsService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    /**
     * Событие файла.
     *
     * @param message конверт
     * @throws IOException битый JSON
     */
    @KafkaListener(topics = Topics.PACKAGE_FILE_V1, groupId = "docproc")
    public void onEvent(String message) throws IOException {
        JsonNode envelope = json.readTree(message);
        service.handle(UUID.fromString(envelope.path("eventId").asText()), envelope.path("type").asText(),
                envelope.path("payload"));
    }
}
