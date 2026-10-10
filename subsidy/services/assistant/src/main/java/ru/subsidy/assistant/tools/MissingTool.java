package ru.subsidy.assistant.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Полнота пакета: каких документов не хватает и почему, текст запроса организации.
 */
@Component
public class MissingTool implements Tool {

    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param json Jackson
     */
    public MissingTool(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String name() {
        return "get_missing";
    }

    @Override
    public String description() {
        return "чего не хватает в пакете: отсутствующие документы с причиной и пункты чек-листа. Аргумент не нужен.";
    }

    @Override
    public JsonNode run(JsonNode factsRs, String argument) {
        JsonNode completeness = factsRs.path(ProtocolTool.PROTOCOL).path("completeness");
        ObjectNode out = json.createObjectNode();
        for (String key : List.of("percent", "missing", "items")) {
            out.set(key, completeness.path(key));
        }
        out.set("requestText", completeness.path("request"));
        return out;
    }
}
