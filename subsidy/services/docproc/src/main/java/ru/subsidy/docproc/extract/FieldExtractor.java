package ru.subsidy.docproc.extract;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.docproc.classify.Prompts;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;

/**
 * Поля документа — моделью по JSON Schema вида (rules.md §1.3); проверяет код (FieldValidator).
 */
@Component
public class FieldExtractor {

    private static final String DATA = "data";
    private static final String CONFIDENCE = "confidence";
    private static final String MARKS = "marks";
    private static final String TYPE = "type";
    private static final String OBJECT = "object";
    private static final String PROPERTIES = "properties";
    private static final String ADDITIONAL = "additionalProperties";
    private static final String REQUIRED = "required";
    private static final String NUMBER = "number";
    private static final String STRING = "string";
    private static final String DESCRIPTION = "description";
    private static final String SIGNATURE = "signature";
    private static final String STAMP = "stamp";
    private static final String STATUS = "status";
    private static final String BASIS = "basis";

    private final LlmGateway llm;
    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param llm модель
     * @param json Jackson
     */
    public FieldExtractor(LlmGateway llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    /**
     * Извлекает поля.
     *
     * @param documentId документ (журнал)
     * @param type вид
     * @param text текст документа
     * @param images картинки страниц без текста или пусто
     * @return ответ модели {data, confidence}
     */
    public Extraction extract(String documentId, DocType type, String text, List<byte[]> images) {
        String system = Prompts.load(Prompts.EXTRACT).replace("{{TYPE_NAME}}", type.name())
                .replace("{{TYPE_CODE}}", type.code());
        String user = text.isBlank() ? "[текстового слоя нет — прочитай документ по изображению]" : text;
        LlmResult result = llm.structured(new LlmRequest("EXTRACTING_FIELDS", documentId, system, user,
                schema(type), images));
        JsonNode marks = result.json().path(MARKS);
        return new Extraction(result.json().path(DATA), result.json().path(CONFIDENCE), result.model(),
                MarkParser.parse(marks, !images.isEmpty()));
    }

    private JsonNode schema(DocType type) {
        ObjectNode root = json.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = root.putObject(PROPERTIES);
        props.set(DATA, type.schema());
        ObjectNode conf = props.putObject(CONFIDENCE).put(TYPE, OBJECT);
        conf.putObject(ADDITIONAL).put(TYPE, NUMBER);
        props.set(MARKS, marksSchema());
        root.putArray(REQUIRED).add(DATA).add(CONFIDENCE).add(MARKS);
        return root;
    }

    private JsonNode marksSchema() {
        ObjectNode marks = json.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = marks.putObject(PROPERTIES);
        props.set(SIGNATURE, markSchema("Подпись уполномоченного лица"));
        props.set(STAMP, markSchema("Печать организации"));
        marks.putArray(REQUIRED).add(SIGNATURE).add(STAMP);
        marks.put(ADDITIONAL, false);
        return marks;
    }

    private JsonNode markSchema(String label) {
        ObjectNode mark = json.createObjectNode().put(TYPE, OBJECT).put(DESCRIPTION, label);
        ObjectNode props = mark.putObject(PROPERTIES);
        props.putObject(STATUS).put(TYPE, STRING).putArray("enum")
                .add("DETECTED").add("NOT_DETECTED").add("UNDETERMINED");
        props.putObject(CONFIDENCE).put(TYPE, NUMBER).put("minimum", 0).put("maximum", 1);
        props.putObject(BASIS).put(TYPE, STRING)
                .put(DESCRIPTION, "Короткая цитата или признак, по которому определено состояние");
        mark.putArray(REQUIRED).add(STATUS).add(CONFIDENCE).add(BASIS);
        mark.put(ADDITIONAL, false);
        return mark;
    }

    /**
     * Ответ модели.
     *
     * @param data значения полей
     * @param confidence уверенность по полям
     * @param model модель
     * @param marks признаки подписи и печати (B2-2)
     */
    public record Extraction(JsonNode data, JsonNode confidence, String model, DocumentMarks marks) {
    }
}
