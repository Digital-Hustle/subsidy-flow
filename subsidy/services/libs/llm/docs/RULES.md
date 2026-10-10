# RULES — libs/llm

Сверху действуют `../../../../rules.md` (красные линии), `../../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём модуле; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `subsidy/docs/API.md` + `./gradlew :libs:llm:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа, проверок и очереди — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `subsidy/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Новый провайдер — пакет `infra/<name>` + WireMock-тест + строка в LLM.md.
- Новая модель — сначала лицензия и строка в DECISIONS.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite); MinIO/S3 для файлов.
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh llm <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, содержимого файлов, распознанного текста и токенов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Ключей в коде/тестах/логах; логирования промптов с текстом документа.
- ⛔ Fallback-ответов с данными — только `LlmUnavailable`.
- Бизнес-логики (виды, правила) в библиотеке.

## Библиотеки
Разрешены: Jackson, networknt, Resilience4j, WireMock (тесты). LangChain4j — только через ADR. Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Сеть/5xx/таймаут → `LlmUnavailable` (docproc → WAITING_MODEL); 401/403 → `LlmMisconfigured`; невалидный JSON дважды → `LlmInvalidOutput`.

## Ограничения
Ollama на CPU — 1–2 параллельных вызова; `keep_alive=30m`.
