package ru.subsidy.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры помощника (app.assistant).
 *
 * @param enabled фича включена (ASSISTANT_ENABLED)
 * @param rulesUrl базовый адрес rules
 * @param maxToolCalls максимум вызовов инструментов на вопрос
 * @param maxQuestionLength максимальная длина вопроса
 * @param historyLimit сколько сообщений истории отдавать
 */
@ConfigurationProperties("app.assistant")
public record AssistantProperties(boolean enabled, String rulesUrl, int maxToolCalls, int maxQuestionLength,
                                  int historyLimit) {
}
