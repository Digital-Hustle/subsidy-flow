# RULES — package

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём модуле; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `subsidy/docs/API.md` + `./gradlew :package:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа, проверок и очереди — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `subsidy/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Каждая проверка — класс `*Check` + тест с реальным «плохим» синтетическим файлом.
- Тип файла — по сигнатуре байт; расширение и `Content-Type` клиента — только для сообщения.
- Пакет Java — `ru.subsidy.package_` (слово `package` зарезервировано), имя сервиса и БД — `package`.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite); MinIO/S3 для файлов.
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh package <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, содержимого файлов, распознанного текста и токенов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Передавать имя файла, путь в архиве или порядок в событие для docproc (ADR-005).
- ⛔ Распаковывать ZIP на диск, доверять путям записей, рекурсивно распаковывать вложенные архивы.
- ⛔ PyMuPDF, poppler, ghostscript, iText.

## Библиотеки
Разрешены: Tika core, PDFBox, TwelveMonkeys, POI, Commons Compress. Запрещены: iText, MinIO. Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Отказ приёма — код из API.md; исключение парсера → `FILE_CORRUPTED`; один плохой файл в ZIP не отменяет остальные (`rejected[]`).
- Чужой пакет → 404; пакет решён → 409 `PACKAGE_DECIDED`.

## Ограничения
Приём без OCR/модели в запросе. ZIP обрабатывается потоково, с лимитом памяти; таймаут открытия файла 5 с.
