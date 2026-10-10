package ru.subsidy.rules.history;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.rules.jooq.Tables;

/**
 * История по организации (B-03): только вставка, UPDATE/DELETE запрещены триггером.
 */
@Service
public class OrgHistoryService {

    private static final int LIMIT = 500;

    private final DSLContext dsl;
    private final ObjectMapper json;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param json Jackson
     * @param clock часы
     */
    public OrgHistoryService(DSLContext dsl, ObjectMapper json, Clock clock) {
        this.dsl = dsl;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Добавляет запись (в транзакции вызывающего).
     *
     * @param inn ИНН организации
     * @param actor кто (userId или system)
     * @param action действие
     * @param packageId пакет
     * @param details подробности (без ПДн)
     * @throws IllegalStateException details не сериализуются
     */
    public void append(String inn, String actor, String action, UUID packageId, Map<String, Object> details) {
        if (inn == null) {
            return;
        }
        try {
            dsl.insertInto(Tables.ORG_HISTORY)
                    .set(Tables.ORG_HISTORY.ID, UuidCreator.getTimeOrderedEpoch())
                    .set(Tables.ORG_HISTORY.INN, inn)
                    .set(Tables.ORG_HISTORY.AT, OffsetDateTime.now(clock))
                    .set(Tables.ORG_HISTORY.ACTOR, actor == null ? "system" : actor)
                    .set(Tables.ORG_HISTORY.ACTION, action)
                    .set(Tables.ORG_HISTORY.PACKAGE_ID, packageId)
                    .set(Tables.ORG_HISTORY.DETAILS, JSONB.valueOf(json.writeValueAsString(details)))
                    .execute();
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("org_history: details не сериализуются", broken);
        }
    }

    /**
     * История организации, новые сверху.
     *
     * @param inn ИНН
     * @return записи
     */
    @Transactional(readOnly = true)
    public List<Entry> history(String inn) {
        return dsl.selectFrom(Tables.ORG_HISTORY).where(Tables.ORG_HISTORY.INN.eq(inn))
                .orderBy(Tables.ORG_HISTORY.AT.desc()).limit(LIMIT)
                .fetch(rec -> new Entry(rec.getAt(), rec.getActor(), rec.getAction(), rec.getPackageId(),
                        read(rec.getDetails().data())));
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("org_history: битый JSON", broken);
        }
    }

    /**
     * Запись истории.
     *
     * @param at когда
     * @param by кто
     * @param action действие
     * @param packageId пакет
     * @param details подробности
     */
    public record Entry(OffsetDateTime at, String by, String action, UUID packageId, JsonNode details) {
    }
}
