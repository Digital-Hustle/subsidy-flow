package ru.subsidy.rules.program;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.rules.engine.declarative.ProgramRules;
import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.rules.ProgramRulesRule;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.recheck.RecheckRequests;

/**
 * Программы и их версии (FR-35): чтение, создание и новая версия — только старшим специалистом.
 */
@Service
public class ProgramService {

    private static final String LATEST = "latest";
    private static final String NOT_FOUND = "Программа не найдена: ";
    private static final String ITEM = "item";
    private static final String TITLE = "title";
    private static final String EXISTS = "Программа с таким кодом уже есть: ";
    private static final String DESCRIPTION = "description";
    private static final String DEADLINE = "deadline";
    private static final int MAX_DESCRIPTION = 2000;

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
     * Все версии программы, новые первыми.
     *
     * @param code код
     * @return версии
     * @throws DomainException PROGRAM_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public List<Program> versions(String code) {
        List<Program> versions = dsl.selectFrom(Tables.PROGRAMS).where(Tables.PROGRAMS.CODE.eq(code))
                .orderBy(Tables.PROGRAMS.VERSION.desc()).fetch(this::toProgram);
        if (versions.isEmpty()) {
            throw new DomainException(ErrorCode.PROGRAM_NOT_FOUND, NOT_FOUND + code);
        }
        return versions;
    }

    /**
     * Идёт ли приём заявок: срок {@code params.deadline} (включительно) не прошёл по часам сервера.
     *
     * @param program программа
     * @return true, если срока нет или он не прошёл
     */
    public boolean accepting(Program program) {
        return program.params().date(DEADLINE).map(deadline -> !LocalDate.now(clock).isAfter(deadline))
                .orElse(true);
    }

    /**
     * Новая программа (FR-35): версия 1, код уникален.
     *
     * @param code код
     * @param name название
     * @param params параметры
     * @param checklist чек-лист
     * @param userId автор
     * @return программа
     * @throws DomainException PROGRAM_EXISTS, VALIDATION_ERROR
     */
    @Transactional
    public Program create(String code, String name, JsonNode params, JsonNode checklist, String userId) {
        validate(params, checklist);
        if (find(code).isPresent()) {
            throw new DomainException(ErrorCode.PROGRAM_EXISTS, EXISTS + code);
        }
        try {
            insert(code, 1, name, false, params, checklist, userId);
        } catch (DuplicateKeyException race) {
            throw new DomainException(ErrorCode.PROGRAM_EXISTS, EXISTS + code);
        }
        return current(code);
    }

    /**
     * Новая версия программы (неизвестный код — версия 1, как раньше); нерешённые пакеты программы перепроверяются.
     *
     * @param code код
     * @param name название (null — прежнее)
     * @param params параметры
     * @param checklist чек-лист
     * @param userId автор
     * @return новая версия
     * @throws DomainException VALIDATION_ERROR
     */
    @Transactional
    public Program update(String code, String name, JsonNode params, JsonNode checklist, String userId) {
        validate(params, checklist);
        Optional<Program> previous = find(code);
        String title = name == null || name.isBlank() ? previous.map(Program::name).orElse(code) : name;
        insert(code, previous.map(Program::version).orElse(0) + 1, title, previous.map(Program::demo).orElse(false),
                params, checklist, userId);
        dsl.select(Tables.PACKAGE_VIEW.PACKAGE_ID).from(Tables.PACKAGE_VIEW)
                .where(Tables.PACKAGE_VIEW.PROGRAM_CODE.eq(code)).and(Tables.PACKAGE_VIEW.STATUS.ne("DECIDED"))
                .fetch(Tables.PACKAGE_VIEW.PACKAGE_ID).forEach(recheck::request);
        return current(code);
    }

    private void validate(JsonNode params, JsonNode checklist) {
        if (params == null || !params.isObject() || checklist == null || !checklist.isArray()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "params — объект, checklist — массив пунктов");
        }
        parseChecklist(checklist);
        try {
            programRules.parse(params.path(ProgramRulesRule.RULES));
        } catch (IllegalArgumentException broken) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, broken.getMessage());
        }
        validateDescription(params.get(DESCRIPTION));
        validateDeadline(params.get(DEADLINE));
    }

    private static void validateDescription(JsonNode description) {
        boolean absent = description == null || description.isNull();
        if (!absent && (!description.isTextual() || description.asText().length() > MAX_DESCRIPTION)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "params.description — строка не длиннее " + MAX_DESCRIPTION + " символов");
        }
    }

    private static void validateDeadline(JsonNode deadline) {
        if (deadline == null || deadline.isNull()) {
            return;
        }
        try {
            LocalDate.parse(deadline.asText());
        } catch (DateTimeParseException broken) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "params.deadline — дата ГГГГ-ММ-ДД");
        }
    }

    private void insert(String code, int version, String name, boolean demo, JsonNode params, JsonNode checklist,
                        String userId) {
        dsl.insertInto(Tables.PROGRAMS)
                .set(Tables.PROGRAMS.CODE, code)
                .set(Tables.PROGRAMS.VERSION, version)
                .set(Tables.PROGRAMS.NAME, name)
                .set(Tables.PROGRAMS.DEMO, demo)
                .set(Tables.PROGRAMS.PARAMS, JSONB.valueOf(params.toString()))
                .set(Tables.PROGRAMS.CHECKLIST, JSONB.valueOf(checklist.toString()))
                .set(Tables.PROGRAMS.CREATED_BY, userId)
                .set(Tables.PROGRAMS.CREATED_AT, OffsetDateTime.now(clock))
                .execute();
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
        var codes = new HashSet<String>();
        for (JsonNode node : checklist) {
            List<String> types = new ArrayList<>();
            node.path("types").forEach(type -> types.add(type.asText()));
            String kind = node.path("kind").asText(ChecklistItem.REQUIRED);
            if (types.isEmpty() || !List.of(ChecklistItem.REQUIRED, ChecklistItem.ONE_OF, ChecklistItem.CONDITIONAL)
                    .contains(kind) || types.stream().anyMatch(type -> !Docs.known(type))) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Пункт чек-листа: kind REQUIRED|ONE_OF|CONDITIONAL и непустой types");
            }
            String item = node.path(ITEM).asText();
            String condition = node.path("condition").asText(null);
            if (item.isBlank() || node.path(TITLE).asText().isBlank() || !codes.add(item)
                    || condition != null && !condition.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Пункт чек-листа: уникальный item, title и condition — код условия проекта");
            }
            items.add(new ChecklistItem(item, node.path(TITLE).asText(), kind, types,
                    condition, node.path("conditionText").asText(null),
                    node.path("single").asBoolean(false)));
        }
        return items;
    }
}
