package ru.subsidy.common;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.inbox.IdempotentConsumer;
import ru.subsidy.common.outbox.OutboxRelay;
import ru.subsidy.common.outbox.OutboxWriter;
import ru.subsidy.common.security.ServiceTokenProvider;
import ru.subsidy.common.web.ProblemErrorHandler;

/**
 * Общие бины сервисов: часы, inbox, outbox (app.outbox.shared=true), токен service account.
 */
@AutoConfiguration
public class CommonAutoConfiguration {

    /**
     * Ошибки фреймворка в формате problem+json (FR-06).
     *
     * @return обработчик
     */
    @Bean
    @ConditionalOnMissingBean
    public ProblemErrorHandler problemErrorHandler() {
        return new ProblemErrorHandler();
    }

    /**
     * Часы приложения.
     *
     * @return UTC
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Идемпотентность потребителей.
     *
     * @param jdbc JDBC
     * @param clock часы
     * @return consumer
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "app.outbox.shared", havingValue = "true")
    public IdempotentConsumer idempotentConsumer(JdbcClient jdbc, Clock clock) {
        return new IdempotentConsumer(jdbc, clock);
    }

    /**
     * Запись событий в outbox.
     *
     * @param jdbc JDBC
     * @param json JSON
     * @param clock часы
     * @return writer
     */
    @Bean
    @ConditionalOnProperty(name = "app.outbox.shared", havingValue = "true")
    public OutboxWriter sharedOutboxWriter(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        return new OutboxWriter(jdbc, json, clock);
    }

    /**
     * Relay outbox → Kafka (в тестах выключается app.outbox.relay=false).
     *
     * @param jdbc JDBC
     * @param kafka Kafka
     * @param tx транзакции
     * @param clock часы
     * @return relay
     */
    @Bean
    @ConditionalOnProperty(name = {"app.outbox.shared", "app.outbox.relay"}, havingValue = "true",
            matchIfMissing = false)
    public OutboxRelay sharedOutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka,
                                         TransactionTemplate tx, Clock clock) {
        return new OutboxRelay(jdbc, kafka, tx, clock);
    }

    /**
     * Токен service account для /internal/**.
     *
     * @param tokenUri token endpoint
     * @param clientId client id
     * @param secret секрет
     * @param clock часы
     * @return провайдер токена
     */
    @Bean
    @ConditionalOnProperty(name = "app.service-account.client-id")
    public ServiceTokenProvider serviceTokenProvider(@Value("${app.service-account.token-uri}") String tokenUri,
                                                     @Value("${app.service-account.client-id}") String clientId,
                                                     @Value("${app.service-account.client-secret}") String secret,
                                                     Clock clock) {
        return new ServiceTokenProvider(tokenUri, clientId, secret, clock);
    }
}
