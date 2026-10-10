package ru.subsidy.rules.program;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.rules.engine.declarative.ProgramRules;
import ru.subsidy.rules.engine.rules.ProgramRulesRule;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.recheck.RecheckRequests;

/**
 * Программы и их версии (FR-35): чтение текущей версии, новая версия — только старшим специалистом.
 */
@Service
public class ProgramService {

    private static final String LATEST = "latest";
    private static final String NOT_FOUND = "Программа не найдена: ";

    private final DSLContext dsl;
    private final ObjectMapper json;
    private final Clock clock;
    private final RecheckRequests recheck;
    private final ProgramRules programRules = new ProgramRules();

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param json Jackson
     * @param clock часы
     * @param recheck перепроверки пакетов программы
     */
    public ProgramService(DSLContext dsl, ObjectMapper json, Clock clock, RecheckRequests recheck) {
        this.dsl = dsl;
        this.json = json;
        this.clock = clock;
        this.recheck = recheck;
    }

    /**
     * Текущая версия программы.
     *
     * @param code код
     * @return программа
     * @throws DomainException PROGRAM_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public Program current(String code) {
        return find(code).orElseThrow(() -> new DomainException(ErrorCode.PROGRAM_NOT_FOUND, NOT_FOUND + code));
    }

    /**
     * Текущая версия, если есть.
     *
     * @param code код
     * @return программа или пусто
     */
    @Transactional(readOnly = true)
    public Optional<Program> find(String code) {
        return dsl.selectFrom(Tables.PROGRAMS).where(Tables.PROGRAMS.CODE.eq(code))
                .orderBy(Tables.PROGRAMS.VERSION.desc()).limit(1).fetchOptional(this::toProgram);
    }

    /**
     * Все программы (текущие версии).
     *
     * @return программы
     */
    @Transactional(readOnly = true)
    public List<Program> all() {
        var latest = DSL.select(Tables.PROGRAMS.CODE, DSL.max(Tables.PROGRAMS.VERSION).as(LATEST))
                .from(Tables.PROGRAMS).groupBy(Tables.PROGRAMS.CODE).asTable("latest_versions");
        return dsl.select(Tables.PROGRAMS.fields()).from(Tables.PROGRAMS)
                .join(latest).on(Tables.PROGRAMS.CODE.eq(latest.field(Tables.PROGRAMS.CODE))
                        .and(Tables.PROGRAMS.VERSION.eq(latest.field(LATEST, Integer.class))))
                .orderBy(Tables.PROGRAMS.CODE).fetch(this::toProgram);
    }

    /**
     * Новая версия программы.
     *
     * @param code код
     * @param name название
     * @param params параметры
     * @param checklist чек-лист
     * @param userId старший специалист
     * @return новая версия
     * @throws DomainException VALIDATION_ERROR при неверной структуре
     */
    @Transactional
    public Program update(String code, String name, JsonNode params, JsonNode checklist, String userId) {
        if (params == null || !params.isObject() || checklist == null || !checklist.isArray()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "params — объект, checklist — массив пунктов");
        }
        parseChecklist(checklist);
        try {
            programRules.parse(params.path(ProgramRulesRule.RULES));
        } catch (IllegalArgumentException broken) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, broken.getMessage());
        }
        Optional<Program> previous = find(code);
        int version = previous.map(Program::version).orElse(0) + 1;
        dsl.insertInto(Tables.PROGRAMS)
                .set(Tables.PROGRAMS.CODE, code)
                .set(Tables.PROGRAMS.VERSION, version)
                .set(Tables.PROGRAMS.NAME, name == null ? previous.map(Program::name).orElse(code) : name)
                .set(Tables.PROGRAMS.DEMO, previous.map(Program::demo).orElse(false))
                .set(Tables.PROGRAMS.PARAMS, JSONB.valueOf(params.toString()))
                .set(Tables.PROGRAMS.CHECKLIST, JSONB.valueOf(checklist.toString()))
                .set(Tables.PROGRAMS.CREATED_BY, userId)
                .set(Tables.PROGRAMS.CREATED_AT, OffsetDateTime.now(clock))
                .execute();
        dsl.select(Tables.PACKAGE_VIEW.PACKAGE_ID).from(Tables.PACKAGE_VIEW)
                .where(Tables.PACKAGE_VIEW.PROGRAM_CODE.eq(code)).and(Tables.PACKAGE_VIEW.STATUS.ne("DECIDED"))
                .fetch(Tables.PACKAGE_VIEW.PACKAGE_ID).forEach(recheck::request);
        return current(code);
    }

    private Program toProgram(Record rec) {
        try {
            JsonNode params = json.readTree(rec.get(Tables.PROGRAMS.PARAMS).data());
            JsonNode checklist = json.readTree(rec.get(Tables.PROGRAMS.CHECKLIST).data());
            return new Program(rec.get(Tables.PROGRAMS.CODE), rec.get(Tables.PROGRAMS.VERSION),
                    rec.get(Tables.PROGRAMS.NAME), rec.get(Tables.PROGRAMS.DEMO), new ProgramParams(params),
                    parseChecklist(checklist), rec.get(Tables.PROGRAMS.CREATED_BY),
                    rec.get(Tables.PROGRAMS.CREATED_AT));
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("programs: битый JSON", broken);
        }
    }

    private static List<ChecklistItem> parseChecklist(JsonNode checklist) {
        List<ChecklistItem> items = new ArrayList<>();
        for (JsonNode node : checklist) {
            List<String> types = new ArrayList<>();
            node.path("types").forEach(type -> types.add(type.asText()));
            String kind = node.path("kind").asText(ChecklistItem.REQUIRED);
            if (types.isEmpty() || !List.of(ChecklistItem.REQUIRED, ChecklistItem.ONE_OF, ChecklistItem.CONDITIONAL)
                    .contains(kind)) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Пункт чек-листа: kind REQUIRED|ONE_OF|CONDITIONAL и непустой types");
            }
            items.add(new ChecklistItem(node.path("item").asText(), node.path("title").asText(), kind, types,
                    node.path("condition").asText(null), node.path("conditionText").asText(null),
                    node.path("single").asBoolean(false)));
        }
        return items;
    }
}
