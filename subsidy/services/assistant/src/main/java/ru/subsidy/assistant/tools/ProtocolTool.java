package ru.subsidy.assistant.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Итог последнего протокола: статус, счётчики и все непройденные проверки с ожидаемым и фактическим значением.
 */
@Component
public class ProtocolTool implements Tool {

    static final String PROTOCOL = "protocol";
    static final String RESULTS = "results";
    static final String RULE = "rule";
    static final String STATUS = "status";

    private static final String PASSED = "PASSED";

    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param json Jackson
     */
    public ProtocolTool(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String name() {
        return "get_protocol";
    }

    @Override
    public String description() {
        return "итог проверки пакета: статус, версия, полнота %, число ошибок и предупреждений, список непройденных "
                + "проверок (rule, status, message, expected, actual, evidence). Аргумент не нужен.";
    }

    @Override
    public JsonNode run(JsonNode factsRs, String argument) {
        JsonNode protocol = factsRs.path(PROTOCOL);
        ObjectNode out = json.createObjectNode();
        if (protocol.isMissingNode() || protocol.isNull()) {
            out.put(STATUS, "NOT_CHECKED_YET");
            return out;
        }
        out.put(STATUS, protocol.path(STATUS).asText());
        for (String key : List.of("version", "failed", "warnings", "needsReview")) {
            out.set(key, protocol.path(key));
        }
        out.set("completenessPercent", protocol.path("completeness").path("percent"));
        ArrayNode problems = out.putArray("problems");
        for (JsonNode result : protocol.path(RESULTS)) {
            if (!PASSED.equals(result.path(STATUS).asText())) {
                problems.add(result);
            }
        }
        return out;
    }
}
