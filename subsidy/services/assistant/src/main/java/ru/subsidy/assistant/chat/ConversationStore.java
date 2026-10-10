package ru.subsidy.assistant.chat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.assistant.jooq.Tables;

/**
 * Диалоги и сообщения в PostgreSQL: один диалог на пару (пакет, пользователь).
 */
@Component
public class ConversationStore {

    /** Роль сообщения пользователя. */
    public static final String USER = "USER";

    /** Роль сообщения помощника. */
    public static final String ASSISTANT = "ASSISTANT";

    private static final String EMPTY_ARRAY = "[]";
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
    public ConversationStore(DSLContext dsl, ObjectMapper json, Clock clock) {
        this.dsl = dsl;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Диалог пользователя по пакету (создаётся при первом вопросе).
     *
     * @param packageId пакет
     * @param userId пользователь
     * @return идентификатор диалога
     */
    public UUID ensure(UUID packageId, String userId) {
        dsl.insertInto(Tables.CONVERSATIONS)
                .set(Tables.CONVERSATIONS.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.CONVERSATIONS.PACKAGE_ID, packageId)
                .set(Tables.CONVERSATIONS.USER_ID, userId)
                .set(Tables.CONVERSATIONS.CREATED_AT, OffsetDateTime.now(clock))
                .onConflictDoNothing().execute();
        return dsl.select(Tables.CONVERSATIONS.ID).from(Tables.CONVERSATIONS)
                .where(Tables.CONVERSATIONS.PACKAGE_ID.eq(packageId))
                .and(Tables.CONVERSATIONS.USER_ID.eq(userId))
                .fetchSingle(Tables.CONVERSATIONS.ID);
    }

    /**
     * Сохраняет вопрос.
     *
     * @param conversationId диалог
     * @param question текст
     */
    public void question(UUID conversationId, String question) {
        dsl.insertInto(Tables.MESSAGES)
                .set(Tables.MESSAGES.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.MESSAGES.CONVERSATION_ID, conversationId)
                .set(Tables.MESSAGES.ROLE, USER)
                .set(Tables.MESSAGES.CONTENT, question)
                .set(Tables.MESSAGES.CREATED_AT, OffsetDateTime.now(clock))
                .execute();
    }

    /**
     * Сохраняет ответ с вызовами инструментов и статусом валидатора.
     *
     * @param conversationId диалог
     * @param draft ответ
     * @param calls вызовы инструментов
     * @param latencyMs время ответа
     * @return сохранённый ответ
     */
    public Answer answer(UUID conversationId, Draft draft, List<ToolCall> calls, long latencyMs) {
        UUID messageId = UuidCreator.getTimeOrderedEpoch();
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.MESSAGES)
                .set(Tables.MESSAGES.ID, messageId)
                .set(Tables.MESSAGES.CONVERSATION_ID, conversationId)
                .set(Tables.MESSAGES.ROLE, ASSISTANT)
                .set(Tables.MESSAGES.CONTENT, draft.answer())
                .set(Tables.MESSAGES.TOOL_CALLS, jsonb(json.valueToTree(calls)))
                .set(Tables.MESSAGES.SOURCES, jsonb(draft.sources()))
                .set(Tables.MESSAGES.VALIDATOR_STATUS, draft.validation().status())
                .set(Tables.MESSAGES.VALIDATOR_DETAILS, jsonb(json.valueToTree(draft.validation().unknown())))
                .set(Tables.MESSAGES.MODEL, draft.model())
                .set(Tables.MESSAGES.LATENCY_MS, latencyMs)
                .set(Tables.MESSAGES.CREATED_AT, now)
                .execute();
        return new Answer(messageId, conversationId, draft.answer(), draft.sources(),
                calls.stream().map(ToolCall::tool).toList(), draft.validation(), draft.model(), now);
    }

    /**
     * Последние сообщения диалога пользователя по пакету, от старых к новым.
     *
     * @param packageId пакет
     * @param userId пользователь
     * @param limit сколько
     * @return сообщения
     */
    public List<MessageView> history(UUID packageId, String userId, int limit) {
        List<MessageView> latest = dsl.select(Tables.MESSAGES.ID, Tables.MESSAGES.ROLE, Tables.MESSAGES.CONTENT,
                        Tables.MESSAGES.SOURCES, Tables.MESSAGES.VALIDATOR_STATUS, Tables.MESSAGES.VALIDATOR_DETAILS,
                        Tables.MESSAGES.CREATED_AT)
                .from(Tables.MESSAGES).join(Tables.CONVERSATIONS)
                .on(Tables.CONVERSATIONS.ID.eq(Tables.MESSAGES.CONVERSATION_ID))
                .where(Tables.CONVERSATIONS.PACKAGE_ID.eq(packageId))
                .and(Tables.CONVERSATIONS.USER_ID.eq(userId))
                .orderBy(Tables.MESSAGES.CREATED_AT.desc(), Tables.MESSAGES.ID.desc())
                .limit(limit)
                .fetch(row -> new MessageView(row.value1(), row.value2(), row.value3(), tree(row.value4()),
                        row.value5(), tree(row.value6()), row.value7()));
        return latest.reversed();
    }

    private JSONB jsonb(JsonNode node) {
        return JSONB.valueOf(node == null ? EMPTY_ARRAY : node.toString());
    }

    private JsonNode tree(JSONB value) {
        try {
            return json.readTree(value == null ? EMPTY_ARRAY : value.data());
        } catch (com.fasterxml.jackson.core.JsonProcessingException broken) {
            return json.createArrayNode();
        }
    }
}
