package ru.subsidy.assistant.journal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ru.subsidy.assistant.jooq.Tables;
import ru.subsidy.llm.api.LlmCallListener;
import ru.subsidy.llm.api.LlmCallRecord;

/**
 * Журнал обращений к модели (FR-53): провайдер, модель, задержка, токены, валидность схемы. Без текста документов.
 */
@Component
public class LlmCallJournal implements LlmCallListener {

    private static final Logger LOG = LoggerFactory.getLogger(LlmCallJournal.class);

    private final DSLContext dsl;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     */
    public LlmCallJournal(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public void onCall(LlmCallRecord call) {
        try {
            dsl.insertInto(Tables.LLM_CALLS)
                    .set(Tables.LLM_CALLS.ID, UUID.randomUUID())
                    .set(Tables.LLM_CALLS.DOCUMENT_ID, call.documentId())
                    .set(Tables.LLM_CALLS.STAGE, call.stage())
                    .set(Tables.LLM_CALLS.PROVIDER, call.provider())
                    .set(Tables.LLM_CALLS.MODEL, call.model())
                    .set(Tables.LLM_CALLS.LATENCY_MS, call.latencyMs())
                    .set(Tables.LLM_CALLS.TOKENS_IN, call.tokensIn())
                    .set(Tables.LLM_CALLS.TOKENS_OUT, call.tokensOut())
                    .set(Tables.LLM_CALLS.SCHEMA_OK, call.schemaValid())
                    .set(Tables.LLM_CALLS.OUTCOME, call.outcome())
                    .set(Tables.LLM_CALLS.AT, OffsetDateTime.now(clock))
                    .execute();
        } catch (RuntimeException failure) {
            LOG.warn("llm call journal failed: {}", failure.getClass().getSimpleName());
        }
    }
}
