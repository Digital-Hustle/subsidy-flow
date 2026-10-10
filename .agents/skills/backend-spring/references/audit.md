# Разбор эталонов: что взяли и что исправили

## sueta-rnd-back (микросервисы, Gradle KTS)
| Взяли | Почему |
|---|---|
| `services/*` с авто-include в `settings.gradle.kts` | новый сервис = новая папка |
| Spring Cloud Gateway с `RewritePath` `/<svc>/api/v1/** → /api/v1/**` | единый вход, сервисы не знают префикса |
| Контроллер интерфейс + `impl` | контракт и OpenAPI отдельно от реализации |
| DTO-record с суффиксами `Rq`/`Rs`, `@Valid`, `@ResponseStatus` | единый стиль |
| MapStruct с `BaseMapperConfig` (`unmappedTargetPolicy = ERROR`) | забытое поле ломает сборку |
| Внедрённый `Clock`, `ExceptionRs`-фабрика, `@JsonInclude(NON_NULL)` | детерминированные тесты, единый формат ошибок |
| `BaseExceptionHandler` + `DomainExceptionHandler` | фреймворк отдельно от домена |
| Liquibase + jOOQ, checkstyle (IntelliJ-стиль, 140 симв.), `http/*.http` | команда это знает |
| CI: detect changed services → матрица | быстро на PR |

| Нашли | Исправили |
|---|---|
| Разные group/пакеты: `ru.ci_trainee`, `rnd.sueta.event_ms`, `ru.zuzex.practice`, `ru.core` | один корень `ru.proxodnaya.<svc>` |
| Разные версии Boot (3.5.0 / 3.5.7), версии в каждом build-файле, свой gradlew у сервиса | version catalog + convention plugins + один wrapper |
| CI по умолчанию `skip-tests: true`; в event-ms единственный тест — `contextLoads` | тесты обязательны, Testcontainers, матрица эндпоинта |
| Пароли в git: `postgres/root`, Vault root token `myroot`, JWT-секрет в `vault-initializer.sh`, MinIO `admin/root1234` | только `${ENV}`, `.env.example` с `NAME=...`, gitleaks |
| Config Server с `fail-fast` + Vault dev-режим (секреты в памяти, теряются при рестарте) | `application.yml` + env; меньше точек отказа |
| Gateway валидирует JWT и передаёт `X-User-Id`/`X-Username`; сервисы верят заголовку, кладут в `ThreadLocalMap` | каждый сервис — resource server, `CurrentUser` из JWT |
| В compose опубликованы порты config-server и auth-ms, `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*` (отдаёт `env`, `heapdump`); `docker-compose.business-logic.yml` пустой — бизнес-сервисы в compose не поднимаются | наружу только Caddy; actuator — `health,info,prometheus` на отдельном порту; весь стек в одном compose |
| Самописная AOP-безопасность `@HasRole` в profile-ms | `@PreAuthorize` + `EndpointSecurityMatrixTest` |
| Баг: `@DeleteMapping("/{id}/photos/{linkedTableId}") void deletePhoto(@PathVariable UUID photoId)` — имя переменной не совпадает → `MissingPathVariableException`, эндпоинт не работает | integration-тест на каждый эндпоинт |
| `getReviewById` объявляет `{id}`, но не использует — нет проверки принадлежности | owner-scoped запросы `findByIdAndOwnerId` |
| Оркестрация (`EventRegistrationServiceImpl`) без `@Transactional`: проверка и запись в разных транзакциях | транзакция на application/command-методе |
| Миграции `V1__`, `V3_` вперемешку, тестовые данные в боевом changelog | `<timestamp>-slug.sql`, `context:dev` |
| `exception/exception` и `exception/custom` в разных сервисах; сообщения валидации — склейка строк | один `exception/custom`, коды сообщений |
| Отдельный count-запрос на каждую страницу | `count(*) over()` |
| MinIO (AGPL) | запрещён п. 5.8.4 — фото/эмбеддинги в Postgres |
| Образы в личный Docker Hub, `container_name`, внешняя сеть | GHCR, compose-проект без фиксированных имён |
| Параллельно `.gitlab-ci.yml` и GitHub Actions | только GitHub Actions |

## certis-api (Kotlin, модульный монолит)
| Взяли | |
|---|---|
| Фичи-first: `features/<f>/{command,query,application,api,spi,gateway,validator}` | внутреннее устройство каждого нашего сервиса |
| Узкие `api`-интерфейсы по возможности (`AccountCommandAccess`), `spi`-порты от потребителя против циклов | |
| Правила `Rs` по умолчанию, `Dto` — только при намеренном переиспользовании; коллекции в обёртке | |
| `ResponseEntity` только для динамических заголовков/бинарных ответов | |
| Все внешние системы — через `...Gateway`; ошибки провайдера переводятся на границе | |
| Порядок методов get → create → update → delete | |
| `units/` и `integrations/`, `AbstractIntegrationTest` с `protected`-зависимостями, given/when/then | |
| Новая env-переменная → `.env.example` `NAME=...` + compose + CI в одном PR | |
| Мысль о `ApplicationClock` | у нас — `Clock` + `MutableClock` в тестах |

| Отличие у нас | Почему |
|---|---|
| Java вместо Kotlin | все 3 бэка пишут на Java |
| Микросервисы, а не монолит | решение команды (ADR-015); границы фич certis = границы внутри сервиса |
| JWT от Keycloak, не свои cookie-токены | SSO, TOTP, роли персонала из коробки |
| Kafka + outbox | события между сервисами |
| Без MinIO | лицензия |
| checkstyle + ArchUnit вместо ktlint/detekt | Java |
