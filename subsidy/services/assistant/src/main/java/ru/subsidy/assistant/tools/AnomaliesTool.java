package ru.subsidy.assistant.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Аномалии (правила ANOMALY_*): круглые суммы, платежи в выходные, цепочки одного дня.
 */
@Component
public class AnomaliesTool implements Tool {

    private static final String PREFIX = "ANOMALY_";

    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param json Jackson
     */
    public AnomaliesTool(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String name() {
        return "get_anomalies";
    }

    @Override
    public String description() {
        return "подозрительные признаки (правила ANOMALY_*) с подтверждающими данными. Аргумент не нужен.";
    }

    @Override
    public JsonNode run(JsonNode factsRs, String argument) {
        ObjectNode out = json.createObjectNode();
        ArrayNode items = out.putArray("anomalies");
        for (JsonNode result : factsRs.path(ProtocolTool.PROTOCOL).path(ProtocolTool.RESULTS)) {
            if (result.path(ProtocolTool.RULE).asText().startsWith(PREFIX)) {
                items.add(result);
            }
        }
        return out;
    }
}
