package ru.subsidy.common.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Контракт событий (PLT-04): каждая запись outbox должна проходить {@code contracts/events/envelope.schema.json}
 * и схему payload {@code contracts/events/<topic>/<type>.schema.json}. Вызывается после интеграционных тестов.
 */
public final class OutboxContract {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final Map<Path, JsonSchema> CACHE = new ConcurrentHashMap<>();
    private static final String COLON = ": ";
    private static final String SLASH = "/";

    private OutboxContract() {
    }

    /**
     * Проверяет все записи таблицы outbox.
     *
     * @param jdbc доступ к БД сервиса
     * @throws AssertionError есть события не по контракту
     */
    public static void verify(JdbcTemplate jdbc) {
        List<Map<String, Object>> rows = jdbc.queryForList("select topic, payload::text as body from outbox");
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            errors.addAll(check(String.valueOf(row.get("topic")), String.valueOf(row.get("body"))));
        }
        if (!errors.isEmpty()) {
            throw new AssertionError("События outbox не по contracts/events:\n" + String.join("\n", errors));
        }
    }

    /**
     * Проверяет одно событие.
     *
     * @param topic топик
     * @param envelopeJson конверт события JSON
     * @return ошибки (пусто — по контракту)
     */
    public static List<String> check(String topic, String envelopeJson) {
        List<String> errors = new ArrayList<>();
        try {
            JsonNode envelope = JSON.readTree(envelopeJson);
            Path root = root();
            report(errors, topic + " конверт", schema(root.resolve("envelope.schema.json")).validate(envelope));
            String type = envelope.path("type").asText();
            Path payloadSchema = root.resolve(topic).resolve(type + ".schema.json");
            if (!Files.exists(payloadSchema)) {
                errors.add(topic + SLASH + type + COLON + "нет схемы " + payloadSchema.getFileName());
            } else {
                report(errors, topic + SLASH + type, schema(payloadSchema).validate(envelope.path("payload")));
            }
        } catch (IOException failure) {
            errors.add(topic + COLON + failure.getMessage());
        }
        return errors;
    }

    private static void report(List<String> errors, String where, Set<ValidationMessage> messages) {
        messages.forEach(message -> errors.add(where + COLON + message.getMessage()));
    }

    private static JsonSchema schema(Path file) {
        return CACHE.computeIfAbsent(file, path -> {
            try {
                return FACTORY.getSchema(Files.readString(path));
            } catch (IOException failure) {
                throw new IllegalStateException("Схема не читается: " + path, failure);
            }
        });
    }

    /**
     * Каталог contracts/events — ищем вверх от рабочего каталога теста (services/svc → subsidy/contracts/events).
     *
     * @return путь к каталогу схем
     * @throws IllegalStateException каталог не найден
     */
    static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("contracts").resolve("events");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Не найден каталог contracts/events");
    }
}
