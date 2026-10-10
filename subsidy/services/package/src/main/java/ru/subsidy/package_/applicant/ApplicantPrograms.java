package ru.subsidy.package_.applicant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import ru.subsidy.package_.pkg.ProgramCatalogClient;

/**
 * Программы для заявителя: открытые для приёма (rules, {@code accepting}) и их чек-листы.
 */
@Component
public class ApplicantPrograms {

    private static final String CODE = "code";
    private static final String NAME = "name";
    private static final String PARAMS = "params";
    private static final String CONDITIONAL = "CONDITIONAL";
    private static final String ACCEPTING = "accepting";

    private final ProgramCatalogClient catalog;

    /**
     * Конструктор.
     *
     * @param catalog каталог rules
     */
    public ApplicantPrograms(ProgramCatalogClient catalog) {
        this.catalog = catalog;
    }

    /**
     * Открытые программы.
     *
     * @return программы
     */
    public List<ProgramItem> open() {
        List<ProgramItem> items = new ArrayList<>();
        for (JsonNode program : catalog.all()) {
            if (program.path(ACCEPTING).asBoolean(true)) {
                items.add(item(program));
            }
        }
        return items;
    }

    /**
     * Программа по коду (в том числе закрытая — для уже созданных заявок).
     *
     * @param code код
     * @return программа
     */
    public Optional<JsonNode> find(String code) {
        return catalog.all().stream().filter(program -> code.equals(program.path(CODE).asText())).findFirst();
    }

    /**
     * Открыта ли программа для новых заявок.
     *
     * @param code код
     * @return true, если есть и идёт приём
     */
    public boolean isOpen(String code) {
        return find(code).map(program -> program.path(ACCEPTING).asBoolean(true)).orElse(false);
    }

    /**
     * Название программы.
     *
     * @param program программа rules
     * @return название или код
     */
    public static String name(JsonNode program) {
        return program.path(NAME).asText(program.path(CODE).asText());
    }

    /**
     * Чек-лист программы для анкеты: условный пункт обязателен, только если условие проекта выполнено.
     *
     * @param program программа rules
     * @param conditions условия проекта из анкеты (null — неизвестны)
     * @return пункты
     */
    public static List<Required> checklist(JsonNode program, Map<String, Boolean> conditions) {
        List<Required> result = new ArrayList<>();
        for (JsonNode item : program.path("checklist")) {
            List<String> types = new ArrayList<>();
            item.path("types").forEach(type -> types.add(type.asText()));
            if (types.isEmpty()) {
                continue;
            }
            boolean conditional = CONDITIONAL.equals(item.path("kind").asText());
            String condition = item.path("condition").asText(null);
            boolean required = !conditional
                    || conditions != null && condition != null && Boolean.TRUE.equals(conditions.get(condition));
            String hint = types.size() > 1 ? "Подойдёт любой из документов этого пункта" : null;
            result.add(new Required(types.getFirst(), types, item.path("title").asText(types.getFirst()), hint,
                    required, conditional ? item.path("conditionText").asText(null) : null));
        }
        return result;
    }

    private static ProgramItem item(JsonNode program) {
        JsonNode params = program.path(PARAMS);
        return new ProgramItem(program.path(CODE).asText(), name(program),
                params.path("description").asText(null), params.path("deadline").asText(null));
    }

    /**
     * Программа в списке заявителя.
     *
     * @param code код
     * @param name название
     * @param description описание
     * @param deadline срок приёма (ISO) или null
     */
    public record ProgramItem(String code, String name, String description, String deadline) {
    }

    /**
     * Пункт чек-листа для заявителя.
     *
     * @param type основной вид документа (ключ пункта)
     * @param types подходящие виды
     * @param title название
     * @param hint подсказка
     * @param required обязателен для этой анкеты
     * @param condition условие по-русски для условного пункта
     */
    public record Required(String type, List<String> types, String title, String hint, boolean required,
                           String condition) {
    }
}
