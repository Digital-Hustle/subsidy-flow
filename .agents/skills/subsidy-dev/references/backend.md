# Backend — специфика «Субсидий»

Общие правила — **скилл `backend-spring`**. Здесь только кейс. Подробно — `docs/ARCHITECTURE.md`, `services/<svc>/docs/`.

## Сервисы
| Сервис | Порт | Владелец | Суть |
|---|---|---|---|
| gateway | 8080 | B1 | JWT Keycloak на всех путях, `/internal/**` наружу не пускает |
| package | 8081 | B1 | организации (ИНН), пакеты, приём файлов и ZIP с проверками, версии, файлы AES-GCM, SSE (Java-пакет `ru.subsidy.package_`) |
| docproc | 8082 | B2 | очередь `jobs`, сегментация и вид по содержимому, поля, нормализация, уверенность, смена типа |
| ocr | 8000 | B2 | Python: текстовый слой и таблицы, рендер, Tesseract, качество, превью |
| rules | 8083 | B3 | чек-листы, комплектность, правила, арифметика `BigDecimal`, протокол, выгрузка, история, статистика |
| assistant | 8084 | B3 | вопросы по пакету: инструменты чтения + валидатор чисел |
| libs/llm | — | B3 | `LlmGateway` + адаптеры (docproc, assistant) |

Новый сервис: `new-service.sh <svc> <port> <Owner>`, маршрут в gateway, compose без `ports:`, БД и роль, client в realm, CI, CODEOWNERS.

## Инварианты
- Имя файла, путь в архиве и порядок не попадают в `package.file.v1` (ADR-005).
- Разбор — только из `jobs`; стадии идемпотентны; модель недоступна → `WAITING_MODEL`.
- Вердикты, суммы и комплектность — только в rules; API не принимает статусы правил с клиента (ADR-009).
- Деньги — `Money`/`BigDecimal`, `numeric(19,2)`, в JSON строками (ADR-007). Протоколы неизменяемы, `org_history` append-only.

## Обязательные тесты
package: каждый код отказа, zip-бомба и path traversal, IDOR. docproc: `ConcurrentClaimTest`, `RestartMidStageTest`, `RenamedFilesSameTypeTest`, `SegmenterCoverageTest`, нормализаторы денег и реквизитов. rules: тест с числами на каждое правило, `MoneyTest`, `RoleMatrixTest`, `HistoryAppendOnlyTest`. assistant: `NumberValidatorTest`, prompt injection. Везде: `ArchitectureTest`, `LlmIsolationTest`.

## Команды
```bash
cd services
./gradlew :rules:check
./gradlew :package:update :package:generateJooq
./gradlew exportOpenApi
node ../../.agents/skills/backend-spring/scripts/be-check.mjs .
cd ocr && uv run ruff check . && uv run mypy src && uv run pytest
```
