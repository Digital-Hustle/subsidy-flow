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
 * (п. 4.2 кейса). Флаг программы с именем из condition (= true) делает условный документ обязательным.
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
    private static final String REQUIRED_MARK = " — обязателен";
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
            List<DocFacts> docs = item.types().stream().flatMap(type -> facts.ofType(type).stream()).toList();
            if (docs.isEmpty()) {
                (conditional(item, program) ? optional : missing).add(lack(item));
            }
            if (item.single() && docs.size() > 1) {
                duplicates.add(duplicate(item, docs));
            }
            items.add(new ItemResult(item.item(), item.title(), item.kind(), item.types(), item.conditionText(),
                    docs.isEmpty() ? ItemResult.MISSING : ItemResult.OK,
                    docs.stream().map(DocFacts::documentId).toList()));
        }
        List<RuleResult> results = new ArrayList<>();
        results.add(kindResult(REQUIRED, ChecklistItem.REQUIRED, items));
        results.add(kindResult(ONE_OF, ChecklistItem.ONE_OF, items));
        results.add(conditionalResult(program, items));
        results.addAll(duplicates.isEmpty()
                ? List.of(RuleResult.passed(DUPLICATE, "Лишних дубликатов нет", List.of())) : duplicates);
        results.add(unknown(facts));
        long applicable = items.stream().filter(item -> !ChecklistItem.CONDITIONAL.equals(item.kind())).count();
        long done = items.stream().filter(item -> !ChecklistItem.CONDITIONAL.equals(item.kind()))
                .filter(item -> ItemResult.OK.equals(item.status())).count();
        int percent = applicable == 0 ? PERCENT : (int) (done * PERCENT / applicable);
        return new Completeness(items, missing, percent, results, request(missing, optional));
    }

    private static boolean conditional(ChecklistItem item, Program program) {
        return ChecklistItem.CONDITIONAL.equals(item.kind())
                && (item.condition() == null || !program.params().flag(item.condition()));
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
        String base = ChecklistItem.ONE_OF.equals(item.kind()) ? "Нужен любой из документов: " : "Обязательный документ: ";
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

    private static RuleResult conditionalResult(Program program, List<ItemResult> items) {
        List<ItemResult> ofKind = items.stream().filter(item -> ChecklistItem.CONDITIONAL.equals(item.kind()))
                .toList();
        List<String> lacking = ofKind.stream().filter(item -> ItemResult.MISSING.equals(item.status()))
                .map(item -> label(program, item)).toList();
        RuleResult result;
        if (ofKind.isEmpty()) {
            result = RuleResult.notApplicable(CONDITIONAL, "Условных документов в перечне программы нет");
        } else if (lacking.isEmpty()) {
            result = RuleResult.passed(CONDITIONAL, "Условные документы на месте", List.of());
        } else if (lacking.stream().anyMatch(text -> text.endsWith(REQUIRED_MARK))) {
            result = RuleResult.failed(CONDITIONAL, "Нет документа, обязательного по условию программы: "
                    + String.join(SEMI, lacking), List.of(), null, null);
        } else {
            result = RuleResult.warning(CONDITIONAL, "Нет условного документа — проверьте, нужен ли он для проекта: "
                    + String.join(SEMI, lacking) + ". Его можно дослать в эту же заявку", List.of());
        }
        return result;
    }

    private static String label(Program program, ItemResult item) {
        String condition = program.checklist().stream().filter(source -> source.item().equals(item.item()))
                .map(ChecklistItem::condition).filter(flag -> flag != null && program.params().flag(flag))
                .findFirst().orElse(null);
        String text = item.title() + (item.condition() == null ? "" : OPEN + item.condition() + CLOSE);
        return condition == null ? text : text + REQUIRED_MARK;
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
