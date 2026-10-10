---
name: backend-spring
description: Use for ANY Java/Spring Boot backend change in a Gradle (Kotlin DSL) microservice monorepo, including subsidy/services. Covers where a class goes (controller interface + impl, Rq/Rs DTOs, MapStruct mappers, feature-first command/query/application packages, api/spi between features, gateways to external systems), jOOQ repositories, Liquibase changesets, transactions, Kafka with transactional outbox and idempotent consumers, ProblemDetail errors, Spring Security resource server, tests (units/integrations with Testcontainers, ArchUnit), Gradle version catalog and convention plugins, Dockerfile, and creating a new service. Also covers the Python face service conventions. Trigger on "добавь эндпоинт", "новый сервис", "миграция", "куда положить класс", "событие в кафку", "почини сборку", "ревью бэка", "напиши тест".
---

# backend-spring

Эталоны команды — `Digital-Hustle/sueta-rnd-back` (микросервисы, Spring Cloud Gateway, Liquibase, jOOQ, CI по изменённым сервисам) и `DanyaChetvyrtov/certis-api` (фичи → command/query/application, api/spi, Rq/Rs, AbstractIntegrationTest). Здесь — их сильные стороны без найденных слабых мест (`references/audit.md`). Для «Субсидий» поверх этого действуют `subsidy/rules.md` и `subsidy/docs/`.

## 0. Перед изменением
1. Определи **сервис** (владелец по `subsidy/AGENTS.md`) и **фичу** внутри него. Чужой сервис — только через его REST `/internal/**` или события. Чужая БД — никогда.
2. Найди ближайший похожий класс в том же сервисе и повтори форму. Шаблоны — `references/service-layout.md`.
3. Меняешь контракт (REST, событие, схему БД) — сначала контракт и тест, потом код.

## 1. Где что лежит (сервис `ru.proxodnaya.<svc>`)
```
api/controller/XController.java        интерфейс: @RequestMapping, @Operation, @PreAuthorize, валидация
api/controller/impl/XControllerImpl     @RestController, тонкий: принципал → mapper → один вызов → mapper
api/dto/request/*Rq   api/dto/response/*Rs   api/dto/*Dto (только если один и тот же ответ у ≥2 эндпоинтов)
api/mapper/XApiMapper                   MapStruct, методы `convert`, unmappedTargetPolicy = ERROR
api/constants/PathConstants             пути и фрагменты маршрутов
features/<feature>/
  command/{model,service,repository,validator}   изменение состояния
  query/{model,service,repository}               чтение, фильтры, страницы, отчёты; без скрытых записей
  application/service                            оркестрация нескольких command/query/gateway; владеет @Transactional
  api/                                           узкий интерфейс для других фич (`WorkerStatusQuery`)
  spi/                                           порт, объявленный потребителем, чтобы не было цикла
  gateway/                                       внешние системы: другой сервис, LLM, face, Kafka-публикация
  model/ constants/ enums/ exception/
messaging/{outbox,consumer}             Kafka: OutboxRelay, @KafkaListener + IdempotentConsumer
shared/                                 маленькие общие value-типы и политики, без зависимостей на фичи
config/ config/properties/              @Configuration, @ConfigurationProperties (record)
exception/                              BaseExceptionHandler (фреймворк), DomainExceptionHandler, custom/*
```
Зависимости: `controller → application | command | query → repository/gateway`. Фичи не видят `command/query/application` друг друга — только `api`/`spi`. Цикл фич — запрещён. Проверка: `ArchitectureTest` (ArchUnit) в каждом сервисе и `scripts/be-check.mjs`.

## 2. Правила, которые чаще всего нарушают
- **Контроллер тонкий.** Никаких репозиториев, правил и транзакций. Пользователь — только из `@AuthenticationPrincipal CurrentUser`, а не из тела/заголовка запроса.
- **`ResponseEntity` — только** для динамических заголовков/бинарных ответов. Иначе DTO + `@ResponseStatus`. Коллекцию голым массивом не возвращаем — обёртка `WorkersRs { List<WorkerRs> workers }` или `PageRs<T>`.
- **Транзакция** — на самом верхнем application/command методе, который должен быть атомарным. Внешний вызов (HTTP, LLM, face) внутри транзакции — запрещён: сначала вызов, потом короткая транзакция.
- **Событие** — только `outbox.add(...)` в той же транзакции, что и изменение. `KafkaTemplate` в бизнес-коде запрещён; им пользуется только `OutboxRelay`.
- **Потребитель идемпотентен**: `idempotentConsumer.once(eventId, () -> ...)`, ключ сообщения = id агрегата (порядок в партиции).
- **Ошибки** — исключения из `exception/custom` (`NotFoundException`, `ConflictException`, `DomainException(code)`), ответ — `ProblemDetail` с `code`, `errors`, `traceId`. Ad-hoc `Map` из контроллера и стектрейсы наружу — нельзя.
- **Время** — внедрённый `Clock`. `LocalDateTime.now()` без аргумента запрещён.
- **Конфиг** — `application.yml` в сервисе, секреты только `${ENV}` без значения по умолчанию. Новая переменная → `infra/.env.example` как `NAME=...` + compose + CI в том же PR.
- **Порядок методов** в интерфейсе и реализации: get/find/list → create → update → delete.
- **Lombok**: `@RequiredArgsConstructor`, `@Getter`, `@Builder` можно; `@Data` на сущностях и `@SneakyThrows` — нельзя. DTO — `record`.
- Без `TODO`/`FIXME`, без `System.out`, без field-injection `@Autowired`.

## 3. Данные
- **Liquibase**: `scripts/new-migration.sh <svc> <slug>` → `db/changelog/changesets/<yyyyMMddHHmmss>-<slug>.sql` (formatted SQL, `--changeset <author>:<id>` + `--rollback`). Master — `includeAll` с сортировкой по имени: параллельные PR не конфликтуют. Смерженный changeset не правим. Тестовые данные — `context:dev`, не в боевом контексте.
- **jOOQ**: генерация из живой схемы (`./gradlew :<svc>:generateJooq` после `update`), сгенерированный код в `build/`, не в git. Репозиторий возвращает модели фичи (`Records.mapping`, `fetchInto`), не `Record`. N+1 — нельзя, делай батч-запрос.
- Подробности, Kafka-топики и outbox — `references/data-messaging.md`.

## 4. Проверка перед PR
```bash
cd subsidy/services
./gradlew :<svc>:check            # compile, checkstyle, unit + integration (Testcontainers), jacoco
./gradlew check                   # если трогал libs/ или build-logic/
node ../../.agents/skills/backend-spring/scripts/be-check.mjs .   # статические правила этого скилла
```
Тесты: `units/` — сервисы с моками репозиториев/gateway; `integrations/` — эндпоинты через MockMvc, наследуют `AbstractIntegrationTest` (Postgres + Kafka в Testcontainers, общие зависимости `protected`). Каждый эндпоинт: 2xx, 400 (валидация), 401, 403 (чужая роль), 404/409 где уместно. `given / when / then`. Подробно — `references/testing.md`.

## 5. Новый сервис
`scripts/new-service.sh <svc> <port> <Owner>` — создаёт модуль из шаблона (build.gradle.kts на convention-плагине, Application, application.yml, changelog, ArchitectureTest, AbstractIntegrationTest, http/<svc>.http). Затем вручную: маршрут в gateway, контейнер и БД в `infra/`, client в Keycloak realm, владелец в CODEOWNERS. Подробно — `references/build.md`.

## 6. Python-сервис (face)
FastAPI + onnxruntime, `uv`, `ruff`, `mypy --strict`, `pytest`. Структура и правила — `references/python-service.md`.

## Справочники
| Тема | Файл |
|---|---|
| Пакеты, шаблоны классов, именование | `references/service-layout.md` |
| Gradle, version catalog, convention plugins, Docker, CI | `references/build.md` |
| Liquibase, jOOQ, транзакции, Kafka, outbox | `references/data-messaging.md` |
| Тесты, Testcontainers, ArchUnit | `references/testing.md` |
| Что API отдаёт фронту: курсоры, options для комбобоксов, SSE с `Last-Event-ID`, `checks[]` киоска, `X-Server-Time`, `Idempotency-Key`, аватары | `references/frontend-contract.md` |
| Безопасность Spring (сверка с практиками) | скилл `springboot-security` (приоритет у этого скилла и SECURITY.md) |
| Python face-сервис | `references/python-service.md` |
| Что взято из эталонов и что исправлено | `references/audit.md` |
