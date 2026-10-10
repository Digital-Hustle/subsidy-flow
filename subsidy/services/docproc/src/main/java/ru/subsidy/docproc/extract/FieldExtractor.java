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
    private static final String TYPE = "type";

    private static final String OBJECT = "object";

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
        return new Extraction(result.json().path(DATA), result.json().path(CONFIDENCE), result.model());
    }

    private JsonNode schema(DocType type) {
        ObjectNode root = json.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = root.putObject("properties");
        props.set(DATA, type.schema());
        ObjectNode conf = props.putObject(CONFIDENCE).put(TYPE, OBJECT);
        conf.putObject("additionalProperties").put(TYPE, "number");
        root.putArray("required").add(DATA).add(CONFIDENCE);
        return root;
    }

    /**
     * Ответ модели.
     *
     * @param data значения полей
     * @param confidence уверенность по полям
     * @param model модель
     */
    public record Extraction(JsonNode data, JsonNode confidence, String model) {
    }
}
