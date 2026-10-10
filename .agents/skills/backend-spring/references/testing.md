# Тесты

```
src/test/java/ru/subsidy/<svc>/
  ArchitectureTest.java            # ArchUnit: правила слоёв (из шаблона, не удалять)
  config/AbstractIntegrationTest.java
  fixture/WorkerFixture.java       # билдеры тестовых данных
  units/<feature>/WorkerCommandServiceTest.java
  integrations/WorkerControllerIntegrationTest.java
```
- `units/` — Mockito, без Spring-контекста. Сервисы с замоканными репозиториями/gateway/outbox. Обязательно: ветки ошибок и откатов.
- `integrations/` — `*IntegrationTest` (суффикс `IT` запрещает checkstyle), наследуют `AbstractIntegrationTest`. MockMvc + реальный Postgres и Kafka в Testcontainers (`@ServiceConnection`, контейнеры `static` и переиспользуются между классами). Внешние gateway (face, LLM, другие сервисы) — `@MockitoBean` в базовом классе.
- Формат: `// given / // when / // then`, имя `methodName_condition_expected` или `@DisplayName` на русском.
- Время — `MutableClock` из `libs/common` (тестовый бин), не `Thread.sleep`.
- JWT в тестах — `jwt().authorities(...)` из `spring-security-test` или `TestTokens.staff(Role.ADMIN)`.

## AbstractIntegrationTest
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected DSLContext dsl;
    @Autowired protected MutableClock clock;
    @MockitoBean protected FaceGateway faceGateway;

    @AfterEach
    void cleanDb() { /* truncate таблиц сервиса, кроме databasechangelog* */ }
}
```

## Матрица эндпоинта (минимум)
| Случай | Ожидание |
|---|---|
| валидный запрос нужной ролью | 2xx + JSON-контракт + эффект в БД + запись в outbox |
| невалидное тело | 400 `ProblemDetail` с `errors` |
| без токена | 401 |
| чужая роль | 403 |
| нет ресурса / конфликт | 404 / 409 с `code` |

`EndpointSecurityMatrixTest` в каждом сервисе обходит все `RequestMappingHandlerMapping` и падает, если у метода нет `@PreAuthorize` (кроме явно публичных).

## ArchitectureTest (шаблон)
- `..api.controller..` не зависит от `..repository..` и `org.jooq..`.
- `..query..` не зависит от `..command..`.
- `..application..` не зависит от `..repository..`.
- `features.(*)..` → `features.(*)..` только через `..api..`/`..spi..` (slices, без циклов).
- `..shared..` не зависит от `..features..` и `..api..`.
- `KafkaTemplate` используется только в `..messaging.outbox..`.
- Нет полей с `@Autowired`, нет `java.time.*.now()` без `Clock`.
