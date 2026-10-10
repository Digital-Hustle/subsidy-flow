package ru.subsidy.rules.protocol;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.outbox.OutboxWriter;
import ru.subsidy.rules.completeness.Completeness;
import ru.subsidy.rules.completeness.CompletenessEngine;
import ru.subsidy.rules.engine.RuleEngine;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.FactsAssembler;
import ru.subsidy.rules.facts.FileFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.history.OrgHistoryService;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.program.Program;
import ru.subsidy.rules.program.ProgramService;

/**
 * Перепроверка пакета: факты → комплектность → правила → новый неизменяемый протокол + PackageChecked (одна транзакция).
 */
@Service
public class ProtocolService {

    /** Полный пакет без вопросов. */
    public static final String COMPLETE = "COMPLETE";
    /** Не хватает документов. */
    public static final String INCOMPLETE = "INCOMPLETE";
    /** Нужна ручная проверка. */
    public static final String NEEDS_REVIEW = "NEEDS_REVIEW";

    private static final String WARNINGS_KEY = "warnings";
    private static final String FAILED_KEY = "failed";
    private static final String VERSION_KEY = "version";
    private static final Logger LOG = LoggerFactory.getLogger(ProtocolService.class);
    private static final List<String> REVIEW_REASONS = List.of("TYPE_UNKNOWN", "TYPE_OTHER");

    private final FactsAssembler assembler;
    private final ProgramService programs;
    private final CompletenessEngine completeness;
    private final RuleEngine engine;
    private final OutboxWriter outbox;
    private final OrgHistoryService history;
    private final DSLContext dsl;
    private final ObjectMapper json;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param assembler факты
     * @param programs программы
     * @param completeness комплектность
     * @param engine правила
     * @param outbox outbox
     * @param history история
     * @param dsl jOOQ
     * @param json Jackson
     * @param clock часы
     */
    public ProtocolService(FactsAssembler assembler, ProgramService programs, CompletenessEngine completeness,
                           RuleEngine engine, OutboxWriter outbox, OrgHistoryService history, DSLContext dsl,
                           ObjectMapper json, Clock clock) {
        this.assembler = assembler;
        this.programs = programs;
        this.completeness = completeness;
        this.engine = engine;
        this.outbox = outbox;
        this.history = history;
        this.dsl = dsl;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Перепроверяет пакет, если разбор всех файлов закончен; новая версия — только если факты изменились.
     *
     * @param packageId пакет
     * @return id нового протокола или пусто
     */
    @Transactional
    public Optional<UUID> check(UUID packageId) {
        Optional<PackageFacts> facts = assembler.assemble(packageId);
        Optional<Program> program = facts.flatMap(found -> programs.find(found.programCode()));
        if (facts.isEmpty() || program.isEmpty() || !ready(facts.get(), packageId)) {
            return Optional.empty();
        }
        return create(facts.get(), program.get());
    }

    private boolean ready(PackageFacts facts, UUID packageId) {
        String status = dsl.select(Tables.PACKAGE_VIEW.STATUS).from(Tables.PACKAGE_VIEW)
                .where(Tables.PACKAGE_VIEW.PACKAGE_ID.eq(packageId)).fetchOne(Tables.PACKAGE_VIEW.STATUS);
        return !"DECIDED".equals(status) && !facts.files().isEmpty() && facts.files().stream().allMatch(FileFacts::done);
    }

    private Optional<UUID> create(PackageFacts facts, Program program) {
        // через текст: сравнение со снимком из jsonb не зависит от типов чисел Jackson (DecimalNode vs DoubleNode)
        JsonNode snapshot = read(write(Map.of("program", program.code() + "@" + program.version(),
                "rules", RuleEngine.RULES_VERSION, "documents", facts.documents(), "files", facts.files(),
                "inn", String.valueOf(facts.inn()), "packageDate", facts.packageDate().toString())));
        Optional<JsonNode> previous = latestFacts(facts.packageId());
        if (previous.isPresent() && previous.get().equals(snapshot)) {
            LOG.debug("facts unchanged package={}", facts.packageId());
            return Optional.empty();
        }
        Completeness complete = completeness.check(facts, program);
        List<RuleResult> results = new ArrayList<>(complete.results());
        results.addAll(engine.run(facts, program.params()));
        final int failed = count(results, RuleStatus.FAILED);
        final int warnings = count(results, RuleStatus.WARNING);
        final int review = needsReview(facts, results);
        final String status = !complete.missing().isEmpty() ? INCOMPLETE : review > 0 ? NEEDS_REVIEW : COMPLETE;
        Outcome outcome = new Outcome(status, complete, failed, warnings, review);
        final int version = dsl.select(DSL.max(Tables.PROTOCOLS.VERSION)).from(Tables.PROTOCOLS)
                .where(Tables.PROTOCOLS.PACKAGE_ID.eq(facts.packageId())).fetchOptional(0, Integer.class)
                .orElse(0) + 1;
        UUID protocolId = insert(facts, program, version, outcome, snapshot);
        saveResults(protocolId, facts, program, results, OffsetDateTime.now(clock));
        Map<String, Object> event = new HashMap<>();
        event.put("packageId", facts.packageId().toString());
        event.put("protocolId", protocolId.toString());
        event.put(VERSION_KEY, version);
        event.put("protocolStatus", status);
        event.put("completeness", complete.percent());
        event.put(FAILED_KEY, failed);
        event.put(WARNINGS_KEY, warnings);
        event.put("needsReview", review);
        outbox.append(Topics.RULES_PACKAGE_V1, "PackageChecked", facts.packageId().toString(), event);
        history.append(facts.inn(), null, "PROTOCOL_CREATED", facts.packageId(), Map.of(VERSION_KEY, version,
                "status", status, FAILED_KEY, failed, WARNINGS_KEY, warnings));
        return Optional.of(protocolId);
    }

    private UUID insert(PackageFacts facts, Program program, int version, Outcome outcome, JsonNode snapshot) {
        final UUID protocolId = UuidCreator.getTimeOrderedEpoch();
        ObjectNode completenessJson = json.createObjectNode();
        completenessJson.put("percent", outcome.complete().percent());
        completenessJson.set("items", json.valueToTree(outcome.complete().items()));
        completenessJson.set("missing", json.valueToTree(outcome.complete().missing()));
        completenessJson.put("request", outcome.complete().request());
        dsl.insertInto(Tables.PROTOCOLS)
                .set(Tables.PROTOCOLS.ID, protocolId)
                .set(Tables.PROTOCOLS.PACKAGE_ID, facts.packageId())
                .set(Tables.PROTOCOLS.VERSION, version)
                .set(Tables.PROTOCOLS.STATUS, outcome.status())
                .set(Tables.PROTOCOLS.PROGRAM_CODE, program.code())
                .set(Tables.PROTOCOLS.PROGRAM_VERSION, program.version())
                .set(Tables.PROTOCOLS.RULES_VERSION, RuleEngine.RULES_VERSION)
                .set(Tables.PROTOCOLS.COMPLETENESS_PCT, outcome.complete().percent())
                .set(Tables.PROTOCOLS.FAILED, outcome.failed())
                .set(Tables.PROTOCOLS.WARNINGS, outcome.warnings())
                .set(Tables.PROTOCOLS.NEEDS_REVIEW, outcome.review())
                .set(Tables.PROTOCOLS.COMPLETENESS, JSONB.valueOf(completenessJson.toString()))
                .set(Tables.PROTOCOLS.FACTS, JSONB.valueOf(snapshot.toString()))
                .set(Tables.PROTOCOLS.CREATED_AT, OffsetDateTime.now(clock))
                .execute();
        return protocolId;
    }

    private void saveResults(UUID protocolId, PackageFacts facts, Program program, List<RuleResult> results,
                             OffsetDateTime now) {
        int position = 0;
        for (RuleResult result : results) {
            dsl.insertInto(Tables.RULE_RESULTS)
                    .set(Tables.RULE_RESULTS.ID, UuidCreator.getTimeOrderedEpoch())
                    .set(Tables.RULE_RESULTS.PROTOCOL_ID, protocolId)
                    .set(Tables.RULE_RESULTS.PACKAGE_ID, facts.packageId())
                    .set(Tables.RULE_RESULTS.PROGRAM_CODE, program.code())
                    .set(Tables.RULE_RESULTS.POSITION, position++)
                    .set(Tables.RULE_RESULTS.RULE, result.rule())
                    .set(Tables.RULE_RESULTS.STATUS, result.status())
                    .set(Tables.RULE_RESULTS.MESSAGE, result.message())
                    .set(Tables.RULE_RESULTS.EVIDENCE, JSONB.valueOf(write(result.evidence())))
                    .set(Tables.RULE_RESULTS.EXPECTED, result.expected())
                    .set(Tables.RULE_RESULTS.ACTUAL, result.actual())
                    .set(Tables.RULE_RESULTS.CREATED_AT, now)
                    .execute();
        }
    }

    private Optional<JsonNode> latestFacts(UUID packageId) {
        return dsl.select(Tables.PROTOCOLS.FACTS).from(Tables.PROTOCOLS)
                .where(Tables.PROTOCOLS.PACKAGE_ID.eq(packageId)).orderBy(Tables.PROTOCOLS.VERSION.desc()).limit(1)
                .fetchOptional(Tables.PROTOCOLS.FACTS).map(JSONB::data).map(this::read);
    }

    private static int count(List<RuleResult> results, String status) {
        return (int) results.stream().filter(result -> status.equals(result.status())).count();
    }

    private static int needsReview(PackageFacts facts, List<RuleResult> results) {
        long docs = facts.documents().stream().filter(ProtocolService::docNeedsReview).count();
        long files = facts.files().stream().filter(FileFacts::broken).count();
        return (int) (docs + files) + count(results, RuleStatus.ERROR);
    }

    private static boolean docNeedsReview(DocFacts doc) {
        return doc.reasonCode() != null && REVIEW_REASONS.contains(doc.reasonCode())
                || doc.fields().values().stream().anyMatch(field -> field.flagged());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("protocol: не сериализуется", broken);
        }
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("protocol: битый JSON", broken);
        }
    }

    /**
     * Итог проверки.
     *
     * @param status статус протокола
     * @param complete комплектность
     * @param failed нарушений
     * @param warnings предупреждений
     * @param review на ручную проверку
     */
    private record Outcome(String status, Completeness complete, int failed, int warnings, int review) {
    }
}
