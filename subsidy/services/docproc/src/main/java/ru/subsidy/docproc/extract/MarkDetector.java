package ru.subsidy.docproc.extract;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.docproc.classify.Prompts;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.ModelInvalidOutputException;
import ru.subsidy.llm.api.ModelUnavailableException;

/**
 * Визуальная проверка подписи и печати (B2-2, FR-33): отдельный вызов vision-модели по изображениям последних
 * страниц документа. Не влияет на извлечение полей: сбой или непонятный ответ → UNDETERMINED, документ
 * разбирается как обычно. Действительность электронной подписи не проверяет.
 */
@Component
public class MarkDetector {

    /** Виды, где по кейсу есть подписант: заявление, гарантийное письмо, согласие на обработку ПД. */
    public static final Set<String> SIGNED_TYPES = Set.of("APPLICATION", "GUARANTEE_LETTER", "PD_CONSENT");
    /** Сколько последних страниц документа показывать модели (подпись и печать — в конце). */
    public static final int LAST_PAGES = 2;

    private static final Logger LOG = LoggerFactory.getLogger(MarkDetector.class);
    private static final String TYPE = "type";
    private static final String OBJECT = "object";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String STATUS = "status";
    private static final String CONFIDENCE = "confidence";
    private static final String BASIS = "basis";
    private static final String SIGNATURE = "signature";
    private static final String STAMP = "stamp";
    private static final String STRING = "string";

    private final LlmGateway llm;
    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param llm модель
     * @param json Jackson
     */
    public MarkDetector(LlmGateway llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    /**
     * Нужна ли визуальная проверка для вида.
     *
     * @param type код вида
     * @return true для видов с подписантом
     */
    public static boolean applies(String type) {
        return SIGNED_TYPES.contains(type);
    }

    /**
     * Определяет подпись и печать по изображениям страниц.
     *
     * @param documentId документ (журнал вызовов)
     * @param type вид
     * @param images PNG последних страниц; пусто — UNDETERMINED без вызова модели
     * @return признаки
     */
    public DocumentMarks detect(String documentId, DocType type, List<byte[]> images) {
        if (images.isEmpty()) {
            return MarkParser.undetermined();
        }
        String system = Prompts.load(Prompts.MARKS).replace("{{TYPE_NAME}}", type.name());
        try {
            JsonNode answer = llm.structured(new LlmRequest("DETECTING_MARKS", documentId, system,
                    "[изображения последних страниц документа]", schema(), images)).json();
            return MarkParser.parse(answer);
        } catch (ModelUnavailableException | ModelInvalidOutputException failure) {
            LOG.info("marks undetermined doc={}: {}", documentId, failure.getClass().getSimpleName());
            return MarkParser.undetermined();
        }
    }

    private JsonNode schema() {
        ObjectNode root = json.createObjectNode().put(TYPE, OBJECT);
        ObjectNode props = root.putObject(PROPERTIES);
        props.set(SIGNATURE, mark("Подпись уполномоченного лица"));
        props.set(STAMP, mark("Печать организации"));
        root.putArray(REQUIRED).add(SIGNATURE).add(STAMP);
        return root;
    }

    private JsonNode mark(String label) {
        ObjectNode mark = json.createObjectNode().put(TYPE, OBJECT).put("description", label);
        ObjectNode props = mark.putObject(PROPERTIES);
        props.putObject(STATUS).put(TYPE, STRING).putArray("enum")
                .add(MarkState.DETECTED).add(MarkState.NOT_DETECTED).add(MarkState.UNDETERMINED);
        props.putObject(CONFIDENCE).put(TYPE, "number").put("minimum", 0).put("maximum", 1);
        props.putObject(BASIS).put(TYPE, STRING);
        mark.putArray(REQUIRED).add(STATUS);
        return mark;
    }
}
