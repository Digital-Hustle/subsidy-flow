package ru.subsidy.rules.engine.declarative;

import java.time.LocalDate;
import java.util.List;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;

/**
 * Что видит правило: факты пакета и сегодняшняя дата.
 *
 * @param facts факты пакета
 * @param today текущая дата в часовом поясе стенда
 */
public record Scope(PackageFacts facts, LocalDate today) {

    /** Все виды из перечня. */
    public static final String ANY = "*";

    /**
     * Уверенно определённые документы видов; «*» — все виды, кроме «прочего».
     *
     * @param types виды
     * @return документы
     */
    public List<DocFacts> docs(List<String> types) {
        boolean any = types.contains(ANY);
        return facts.documents().stream()
                .filter(doc -> !"TYPE_UNKNOWN".equals(doc.reasonCode()))
                .filter(doc -> any ? !Docs.OTHER.equals(doc.type()) : types.contains(doc.type()))
                .toList();
    }

    /**
     * Первый документ вида.
     *
     * @param type вид
     * @return документ или null
     */
    public DocFacts first(String type) {
        return facts.ofType(type).stream().findFirst().orElse(null);
    }
}
