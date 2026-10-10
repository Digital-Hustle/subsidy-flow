package ru.subsidy.docproc.classify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.docproc.config.DocprocProperties;
import ru.subsidy.docproc.text.PageText;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;

/**
 * Сегментация файла на документы и вид каждого — моделью по содержимому страниц (rules.md §1.1).
 * Имя файла, расширение и порядок в архиве сюда не попадают: на входе только текст и картинки страниц.
 */
@Component
public class Classifier {

    private static final String SEGMENTS = "segments";
    private static final String FROM_PAGE = "fromPage";
    private static final String TO_PAGE = "toPage";
    private static final String TYPE = "type";
    private static final String CONFIDENCE = "confidence";
    private static final String EVIDENCE = "evidence";
    private static final String INTEGER = "integer";
    private static final String PROPS = "properties";
    private static final String PARAGRAPH = "\n\n";
    private static final String DASH = " — ";
    private static final String OBJECT = "object";
    private static final String MINIMUM = "minimum";
    private static final String STRING = "string";
    private static final String REQUIRED = "required";
    private static final String ADDITIONAL = "additionalProperties";
    private static final int SCALE = 3;

    private final LlmGateway llm;
    private final TypeRegistry registry;
    private final ObjectMapper json;
    private final DocprocProperties props;

    /**
     * Конструктор.
     *
     * @param llm модель
     * @param registry реестр видов
     * @param json Jackson
     * @param props настройки
     */
    public Classifier(LlmGateway llm, TypeRegistry registry, ObjectMapper json, DocprocProperties props) {
        this.llm = llm;
        this.registry = registry;
        this.json = json;
        this.props = props;
    }

    /**
     * Делит файл на документы.
     *
     * @param fileId файл (для журнала)
     * @param pages страницы
     * @param images картинки страниц без текста (для vision) или пусто
     * @return сегменты, покрывающие все страницы без пересечений
     */
    public List<Segment> segment(String fileId, List<PageText> pages, List<byte[]> images) {
        String system = Prompts.load(Prompts.SEGMENT).replace("{{TYPES}}", typesList());
        StringBuilder user = new StringBuilder("Страниц в файле: ").append(pages.size()).append(PARAGRAPH);
        for (PageText page : pages) {
            String text = page.hasText() ? page.text() : "[страница без текстового слоя — см. изображение]";
            if (text.length() > props.maxCharsPerPage()) {
                text = text.substring(0, props.maxCharsPerPage()) + " …";
            }
            user.append("=== Страница ").append(page.page()).append(" ===\n").append(text).append(PARAGRAPH);
        }
        LlmResult result = llm.structured(new LlmRequest("CLASSIFYING", fileId, system, user.toString(),
                schema(), images));
        return cover(parse(result.json()), pages.size());
    }

    private List<Segment> parse(JsonNode answer) {
        List<Segment> segments = new ArrayList<>();
        for (JsonNode node : answer.path(SEGMENTS)) {
            String type = registry.find(node.path(TYPE).asText()).map(DocType::code).orElse(TypeRegistry.OTHER);
            BigDecimal confidence = BigDecimal.valueOf(Math.clamp(node.path(CONFIDENCE).asDouble(0), 0, 1))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            segments.add(new Segment(node.path(FROM_PAGE).asInt(1), node.path(TO_PAGE).asInt(1), type, confidence,
                    node.path(EVIDENCE).asText(null)));
        }
        return segments;
    }

    /**
     * Приводит ответ модели к покрытию 1..pages без пропусков и пересечений.
     *
     * @param raw сегменты модели
     * @param pages страниц в файле
     * @return сегменты
     */
    static List<Segment> cover(List<Segment> raw, int pages) {
        List<Segment> sorted = raw.stream()
                .map(seg -> new Segment(Math.clamp(seg.fromPage(), 1, pages),
                        Math.clamp(Math.max(seg.fromPage(), seg.toPage()), 1, pages), seg.type(), seg.confidence(),
                        seg.evidence()))
                .sorted(Comparator.comparingInt(Segment::fromPage)).toList();
        List<Segment> result = new ArrayList<>();
        int next = 1;
        for (Segment seg : sorted) {
            if (seg.toPage() < next) {
                continue;
            }
            int from = result.isEmpty() ? 1 : next;
            result.add(new Segment(from, seg.toPage(), seg.type(), seg.confidence(), seg.evidence()));
            next = seg.toPage() + 1;
        }
        if (result.isEmpty()) {
            result.add(new Segment(1, pages, TypeRegistry.OTHER, BigDecimal.ZERO.setScale(SCALE), null));
        } else if (next <= pages) {
            Segment last = result.removeLast();
            result.add(new Segment(last.fromPage(), pages, last.type(), last.confidence(), last.evidence()));
        }
        return result;
    }

    private String typesList() {
        return registry.all().stream().map(type -> "- " + type.code() + DASH + type.name() + DASH + type.hints())
                .collect(Collectors.joining("\n"));
    }

    private JsonNode schema() {
        ObjectNode item = json.createObjectNode().put(TYPE, OBJECT);
        ObjectNode itemProps = item.putObject(PROPS);
        itemProps.putObject(FROM_PAGE).put(TYPE, INTEGER).put(MINIMUM, 1);
        itemProps.putObject(TO_PAGE).put(TYPE, INTEGER).put(MINIMUM, 1);
        ArrayNode codes = itemProps.putObject(TYPE).put(TYPE, STRING).putArray("enum");
        registry.all().forEach(type -> codes.add(type.code()));
        itemProps.putObject(CONFIDENCE).put(TYPE, "number");
        itemProps.putObject(EVIDENCE).put(TYPE, STRING);
        item.putArray(REQUIRED).add(FROM_PAGE).add(TO_PAGE).add(TYPE).add(CONFIDENCE).add(EVIDENCE);
        item.put(ADDITIONAL, false);
        ObjectNode root = json.createObjectNode().put(TYPE, OBJECT);
        root.putObject(PROPS).putObject(SEGMENTS).put(TYPE, "array").put("minItems", 1).set("items", item);
        root.putArray(REQUIRED).add(SEGMENTS);
        root.put(ADDITIONAL, false);
        return root;
    }
}
