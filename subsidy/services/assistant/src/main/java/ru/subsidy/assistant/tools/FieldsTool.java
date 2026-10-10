package ru.subsidy.assistant.tools;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Извлечённые поля документов пакета (значение, источник, уверенность), по виду документа или все.
 */
@Component
public class FieldsTool implements Tool {

    private static final String DOCUMENTS = "documents";
    private static final String TYPE = "type";
    private static final String FIELDS = "fields";

    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param json Jackson
     */
    public FieldsTool(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String name() {
        return "get_fields";
    }

    @Override
    public String description() {
        return "поля документов пакета: documentId, вид, страницы, поля {значение, источник, уверенность}. "
                + "Аргумент — вид документа (APPLICATION, PD_CONSENT, CERTIFICATE, PRESENTATION, COST_JUSTIFICATION, "
                + "BANK_STATEMENT, PROPERTY_DOC, GUARANTEE_LETTER, GIMS_DECLARATION, OTHER) или пусто для всех.";
    }

    @Override
    public JsonNode run(JsonNode factsRs, String argument) {
        String type = argument == null ? "" : argument.strip().toUpperCase(Locale.ROOT);
        ObjectNode out = json.createObjectNode();
        ArrayNode docs = out.putArray(DOCUMENTS);
        for (JsonNode doc : factsRs.path("facts").path(DOCUMENTS)) {
            if (type.isEmpty() || type.equals(doc.path(TYPE).asText())) {
                ObjectNode item = docs.addObject();
                copy(doc, item, List.of("documentId", TYPE, "fromPage", "toPage"));
                ObjectNode fields = item.putObject(FIELDS);
                doc.path(FIELDS).properties().forEach(entry -> {
                    ObjectNode field = fields.putObject(entry.getKey());
                    copy(entry.getValue(), field, List.of("value", "source", "confidence"));
                });
            }
        }
        return out;
    }

    private static void copy(JsonNode from, ObjectNode into, List<String> keys) {
        keys.forEach(key -> into.set(key, from.path(key)));
    }
}
