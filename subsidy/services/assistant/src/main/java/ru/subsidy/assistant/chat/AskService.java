package ru.subsidy.assistant.chat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.subsidy.assistant.config.AssistantProperties;
import ru.subsidy.assistant.rules.RulesClient;
import ru.subsidy.assistant.tools.Tool;
import ru.subsidy.assistant.validate.NumberValidator;
import ru.subsidy.assistant.validate.Validation;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmRequest;
import ru.subsidy.llm.api.LlmResult;
import ru.subsidy.llm.api.ModelUnavailableException;

/**
 * Вопрос по пакету: цикл «модель → инструмент → модель» (≤ maxToolCalls), затем сверка чисел и сохранение.
 */
@Service
public class AskService {

    private static final String OBJECT = "object";
    private static final String ENUM = "enum";

    private static final String STAGE = "ASSISTANT";
    private static final String ACTION = "action";
    private static final String ANSWER = "answer";
    private static final String TOOL = "tool";
    private static final String ARGUMENT = "argument";
    private static final String SOURCES = "sources";
    private static final String TYPE = "type";
    private static final String STRING = "string";
    private static final String PROPERTIES = "properties";
    private static final int CONTEXT_MESSAGES = 6;

    private final RulesClient rules;
    private final LlmGateway llm;
    private final NumberValidator validator;
    private final ConversationStore store;
    private final AssistantProperties props;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, Tool> tools;

    /**
     * Конструктор.
     *
     * @param rules клиент rules
     * @param llm модель
     * @param validator сверка чисел
     * @param store диалоги
     * @param props настройки
     * @param json Jackson
     * @param clock часы
     * @param tools инструменты
     */
    public AskService(RulesClient rules, LlmGateway llm, NumberValidator validator, ConversationStore store,
                      AssistantProperties props, ObjectMapper json, Clock clock, List<Tool> tools) {
        this.rules = rules;
        this.llm = llm;
        this.validator = validator;
        this.store = store;
        this.props = props;
        this.json = json;
        this.clock = clock;
        this.tools = tools.stream().collect(Collectors.toMap(Tool::name, Function.identity()));
    }

    /**
     * Отвечает на вопрос специалиста по пакету.
     *
     * @param packageId пакет
     * @param userId пользователь
     * @param question вопрос
     * @return ответ со статусом валидатора
     * @throws DomainException NOT_FOUND (помощник выключен), PACKAGE_NOT_FOUND, MODEL_UNAVAILABLE
     */
    public Answer ask(UUID packageId, String userId, String question) {
        requireEnabled();
        long started = clock.millis();
        JsonNode facts = rules.facts(packageId);
        UUID conversationId = store.ensure(packageId, userId);
        List<MessageView> context = store.history(packageId, userId, CONTEXT_MESSAGES);
        store.question(conversationId, question);
        List<ToolCall> calls = new ArrayList<>();
        Draft draft;
        try {
            draft = loop(facts, question, context, calls);
        } catch (ModelUnavailableException failure) {
            throw new DomainException(ErrorCode.MODEL_UNAVAILABLE,
                    "Модель недоступна, вопрос сохранён — повторите позже");
        }
        return store.answer(conversationId, draft, calls, clock.millis() - started);
    }

    /**
     * История диалога пользователя по пакету.
     *
     * @param packageId пакет
     * @param userId пользователь
     * @return сообщения от старых к новым
     */
    public List<MessageView> history(UUID packageId, String userId) {
        requireEnabled();
        rules.facts(packageId);
        return store.history(packageId, userId, props.historyLimit());
    }

    private void requireEnabled() {
        if (!props.enabled()) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Помощник выключен (ASSISTANT_ENABLED=false)");
        }
    }

    private Draft loop(JsonNode facts, String question, List<MessageView> context, List<ToolCall> calls) {
        String system = Prompts.system(List.copyOf(tools.values()), props.maxToolCalls());
        LlmResult result = null;
        boolean answered = false;
        for (int step = 0; step <= props.maxToolCalls() && !answered; step++) {
            boolean last = step == props.maxToolCalls();
            result = llm.structured(new LlmRequest(STAGE, null, system, user(question, context, calls, last),
                    schema(last), List.of()));
            JsonNode reply = result.json();
            Tool tool = tools.get(reply.path(TOOL).asText());
            if (!last && TOOL.equals(reply.path(ACTION).asText()) && tool != null) {
                String argument = reply.path(ARGUMENT).asText("");
                calls.add(new ToolCall(tool.name(), argument, tool.run(facts, argument)));
            } else {
                answered = true;
            }
        }
        JsonNode reply = result.json();
        String answer = reply.path(ANSWER).asText("").strip();
        if (answer.isEmpty()) {
            answer = "Модель не дала ответа по материалам пакета — переформулируйте вопрос.";
        }
        Validation validation = validator.validate(answer, question,
                calls.stream().map(ToolCall::result).toList());
        JsonNode sources = reply.path(SOURCES).isArray() ? reply.path(SOURCES) : json.createArrayNode();
        return new Draft(answer, sources, validation, result.model());
    }

    private String user(String question, List<MessageView> context, List<ToolCall> calls, boolean last) {
        StringBuilder text = new StringBuilder();
        if (!context.isEmpty()) {
            text.append("Предыдущие сообщения:\n");
            context.forEach(message -> text.append(message.role()).append(": ").append(message.content())
                    .append('\n'));
        }
        text.append("Вопрос: ").append(question).append('\n');
        if (!calls.isEmpty()) {
            text.append("Результаты инструментов:\n");
            calls.forEach(call -> text.append(call.tool()).append('(').append(call.argument()).append(") → ")
                    .append(call.result().toString()).append('\n'));
        }
        if (last) {
            text.append("Лимит вызовов исчерпан — дай ответ (action=answer) по имеющимся результатам.\n");
        }
        return text.toString();
    }

    private JsonNode schema(boolean answerOnly) {
        ObjectNode root = json.createObjectNode().put(TYPE, OBJECT);
        ObjectNode properties = root.putObject(PROPERTIES);
        ArrayNode actions = properties.putObject(ACTION).put(TYPE, STRING).putArray(ENUM);
        actions.add(ANSWER);
        if (!answerOnly) {
            actions.add(TOOL);
            ArrayNode names = properties.putObject(TOOL).put(TYPE, STRING).putArray(ENUM);
            tools.keySet().stream().sorted().forEach(names::add);
            properties.putObject(ARGUMENT).put(TYPE, STRING);
        }
        properties.putObject(ANSWER).put(TYPE, STRING);
        ObjectNode source = properties.putObject(SOURCES).put(TYPE, "array").putObject("items").put(TYPE, OBJECT);
        ObjectNode sourceProps = source.putObject(PROPERTIES);
        sourceProps.putObject("documentId").put(TYPE, STRING);
        sourceProps.putObject("field").put(TYPE, STRING);
        sourceProps.putObject("rule").put(TYPE, STRING);
        root.putArray("required").add(ACTION);
        return root;
    }
}
