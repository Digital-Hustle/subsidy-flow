# RULES — rules

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём модуле; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `subsidy/docs/API.md` + `./gradlew :rules:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа, проверок и очереди — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `subsidy/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Новое правило: строка в `../../../docs/RULES-CATALOG.md` → тест с числами (red) → класс `*Rule` (green).
- Деньги только через `Money`; сравнение с допуском; округление в одном месте.
- Каждое действие специалиста — `org_history` в той же транзакции.
- Параметры программ — только из `programs.params`; версия в протоколе.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite); MinIO/S3 для файлов.
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh rules <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, содержимого файлов, распознанного текста и токенов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Принимать с клиента статусы правил, суммы-итоги или «пакет полный» — только значения полей как ввод.
- ⛔ `double`/`float` для денег, `new BigDecimal(double)`.
- ⛔ PASSED при отсутствии данных; изменение старых протоколов.
- ⛔ Вызов модели для вердикта (ADR-006) — модель только в docproc/assistant.
- ⛔ UPDATE/DELETE в `org_history`.

## Библиотеки
Разрешены: Apache POI, PDFBox. Запрещены: iText (AGPL), движки правил с несовместимой лицензией (Drools — Apache, но только через ADR). Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Протокола ещё нет (идёт разбор) → 409 `NOT_CHECKED_YET` со стадией; правка поля решённого пакета → 409 `PACKAGE_DECIDED`.
- Исключение одного правила → результат `ERROR` с кодом, остальные правила выполняются; протокол помечается.

## Ограничения
Перепроверка пакета из 40 документов ≤ 2 с. Экспорт — потоково.
