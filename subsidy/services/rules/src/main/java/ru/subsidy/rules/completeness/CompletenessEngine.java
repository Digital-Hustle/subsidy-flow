package ru.subsidy.rules.completeness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ChecklistItem;
import ru.subsidy.rules.program.Program;

/**
 * Комплектность по чек-листу версии программы: обязательные, «один из», условные, дубликаты, неизвестные.
 * Нет обязательного — ошибка (протокол INCOMPLETE); нет условного — замечание и подсказка «можно дослать»
 * (п. 4.2 кейса). Условие относится к конкретному проекту: application.conditions, а не params программы.
 * Считается по определённому виду документа, а не по файлу.
 */
@Component
public class CompletenessEngine {

    private static final String SEMI = "; ";
    private static final String REQUIRED = "CHK_REQUIRED";
    private static final String ONE_OF = "CHK_ONE_OF";
    private static final String CONDITIONAL = "CHK_CONDITIONAL";
    private static final String DUPLICATE = "CHK_DUPLICATE";
    private static final String UNKNOWN = "CHK_UNKNOWN";
    private static final String OR = " или ";
    private static final String OPEN = " (";
    private static final String CLOSE = ")";
    private static final int PERCENT = 100;

    /**
     * Проверяет комплектность.
     *
     * @param facts факты
     * @param program версия программы
     * @return пункты, недостающее, CHK_* и текст запроса
     */
    public Completeness check(PackageFacts facts, Program program) {
        List<ItemResult> items = new ArrayList<>();
        List<Missing> missing = new ArrayList<>();
        List<RuleResult> duplicates = new ArrayList<>();
        List<Missing> optional = new ArrayList<>();
        for (ChecklistItem item : program.checklist()) {
            addItem(item, facts, items, missing, optional, duplicates);
        }
        List<RuleResult> results = new ArrayList<>();
        results.add(kindResult(REQUIRED, ChecklistItem.REQUIRED, items));
        results.add(kindResult(ONE_OF, ChecklistItem.ONE_OF, items));
        results.add(conditionalResult(items));
        results.addAll(duplicates.isEmpty()
                ? List.of(RuleResult.passed(DUPLICATE, "Лишних дубликатов нет", List.of())) : duplicates);
        results.add(unknown(facts));
        long applicable = items.stream().filter(item -> Boolean.TRUE.equals(item.applicable())).count();
        long done = items.stream().filter(item -> Boolean.TRUE.equals(item.applicable()))
                .filter(item -> ItemResult.OK.equals(item.status())).count();
        int percent = applicable == 0 ? PERCENT : (int) (done * PERCENT / applicable);
        return new Completeness(items, missing, percent, results, request(missing, optional));
    }

    private static void addItem(ChecklistItem item, PackageFacts facts, List<ItemResult> items, List<Missing> missing,
                                 List<Missing> optional, List<RuleResult> duplicates) {
        List<DocFacts> docs = item.types().stream().flatMap(type -> facts.ofType(type).stream()).toList();
        Boolean applies = applicability(item, facts);
        if (docs.isEmpty() && !Boolean.FALSE.equals(applies)) {
            (ChecklistItem.CONDITIONAL.equals(item.kind()) ? optional : missing).add(lack(item));
        }
        if (!Boolean.FALSE.equals(applies) && item.single() && docs.size() > 1) {
            duplicates.add(duplicate(item, docs));
        }
        String status = Boolean.FALSE.equals(applies) ? ItemResult.NOT_APPLICABLE
                : docs.isEmpty() ? ItemResult.MISSING : ItemResult.OK;
        items.add(new ItemResult(item.item(), item.title(), item.kind(), item.types(), item.conditionText(), status,
                docs.stream().map(DocFacts::documentId).toList(), applies));
    }

    private static Boolean applicability(ChecklistItem item, PackageFacts facts) {
        if (!ChecklistItem.CONDITIONAL.equals(item.kind())) {
            return Boolean.TRUE;
        }
        if (item.condition() == null || facts.application() == null) {
            return null;
        }
        var value = facts.application().path("conditions").path(item.condition());
        return value.isBoolean() ? value.asBoolean() : null;
    }

    private static Missing lack(ChecklistItem item) {
        return new Missing(item.item(), item.types().stream().map(Docs::title).collect(Collectors.joining(OR)),
                reason(item));
    }

    private static RuleResult duplicate(ChecklistItem item, List<DocFacts> docs) {
        return RuleResult.warning(DUPLICATE, "Документов «" + item.title() + "» — " + docs.size() + ", нужен один",
                docs.stream().map(doc -> new Evidence(doc.documentId(), null, doc.fromPage())).toList());
    }

    private static String reason(ChecklistItem item) {
        String base = ChecklistItem.ONE_OF.equals(item.kind()) ? "Нужен любой из документов: "
                : ChecklistItem.CONDITIONAL.equals(item.kind()) ? "Условный документ: " : "Обязательный документ: ";
        String text = base + item.title();
        return item.conditionText() == null ? text : text + OPEN + item.conditionText() + CLOSE;
    }

    private static RuleResult kindResult(String code, String kind, List<ItemResult> items) {
        List<ItemResult> ofKind = items.stream().filter(item -> kind.equals(item.kind())).toList();
        List<ItemResult> lacking = ofKind.stream().filter(item -> ItemResult.MISSING.equals(item.status())).toList();
        RuleResult result;
        if (ofKind.isEmpty() || ofKind.stream().allMatch(item -> ItemResult.NOT_APPLICABLE.equals(item.status()))) {
            result = RuleResult.notApplicable(code, "Таких пунктов в чек-листе нет или условие не выполнено");
        } else if (lacking.isEmpty()) {
            result = RuleResult.passed(code, "Все документы на месте", List.of());
        } else {
            result = RuleResult.failed(code, "Нет документа: " + lacking.stream().map(ItemResult::title)
                    .collect(Collectors.joining(SEMI)), List.of(), null, null);
        }
        return result;
    }

    private static RuleResult conditionalResult(List<ItemResult> items) {
        List<ItemResult> ofKind = items.stream().filter(item -> ChecklistItem.CONDITIONAL.equals(item.kind()))
                .filter(item -> !Boolean.FALSE.equals(item.applicable()))
                .toList();
        List<String> lacking = ofKind.stream().filter(item -> ItemResult.MISSING.equals(item.status()))
                .map(item -> item.title() + (item.applicable() == null ? " — применимость для проекта не указана"
                        : " — применяется к проекту")).toList();
        RuleResult result;
        if (ofKind.isEmpty()) {
            result = RuleResult.notApplicable(CONDITIONAL, "Условные документы не применяются к этому проекту");
        } else if (lacking.isEmpty()) {
            result = RuleResult.passed(CONDITIONAL, "Условные документы на месте", List.of());
        } else {
            result = RuleResult.warning(CONDITIONAL, "Нет условного документа — проверьте, нужен ли он для проекта: "
                    + String.join(SEMI, lacking) + ". Его можно дослать в эту же заявку", List.of());
        }
        return result;
    }

    private static RuleResult unknown(PackageFacts facts) {
        List<UUID> unclear = facts.documents().stream()
                .filter(doc -> Docs.OTHER.equals(doc.type()) || "TYPE_UNKNOWN".equals(doc.reasonCode()))
                .map(DocFacts::documentId).toList();
        long broken = facts.files().stream().filter(file -> file.broken()).count();
        if (unclear.isEmpty() && broken == 0) {
            return RuleResult.passed(UNKNOWN, "Все документы распознаны", List.of());
        }
        return RuleResult.warning(UNKNOWN, "Требуют внимания: документов с неясным видом — " + unclear.size()
                + ", нечитаемых файлов — " + broken, unclear.stream().map(id -> new Evidence(id, null, null)).toList());
    }

    private static String request(List<Missing> missing, List<Missing> optional) {
        if (missing.isEmpty() && optional.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (!missing.isEmpty()) {
            text.append("Для рассмотрения заявки на субсидию просим представить: ")
                    .append(missing.stream().map(Missing::type).collect(Collectors.joining(SEMI))).append('.');
        }
        if (!optional.isEmpty()) {
            text.append(text.isEmpty() ? "" : " ").append("Если это применимо к проекту, дошлите также: ")
                    .append(optional.stream().map(Missing::reason).collect(Collectors.joining(SEMI))).append('.');
        }
        return text.toString();
    }
}
