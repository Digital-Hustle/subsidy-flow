package ru.subsidy.package_.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import ru.subsidy.common.testing.OutboxContract;

/**
 * База для всех *IntegrationTest: Postgres и Kafka в Testcontainers, общие зависимости — protected.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        System.setProperty("FILES_ENCRYPTION_KEY", java.util.Base64.getEncoder().encodeToString(key));
        System.setProperty("STAFF_TOKEN_SIGNING_KEY", StaffKeys.pkcs8());
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Каждое событие, записанное тестом в outbox, — по contracts/events (PLT-04).
     *
     * @param jdbc доступ к БД сервиса
     */
    @AfterEach
    void outboxMatchesContracts(@Autowired JdbcTemplate jdbc) {
        OutboxContract.verify(jdbc);
    }
}
