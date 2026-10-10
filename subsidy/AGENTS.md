# AGENTS.md — кейс «Субсидии» (папка `subsidy/`)

Проект: **«Субсидии»** — автоматическая проверка пакета документов на получение субсидии. Хакатон «СберБизнесВайб», кейс 9.1.3.
Судьи проверяют вживую на нашем стенде. Есть условия допуска (Д1–Д12, зачёт/незачёт) и снятия с конкурса. Самые дорогие блоки — **определение вида документа по содержимому (25 баллов)** и **извлечение полей + правила (20)**: всё, что честно повышает точность типа и полей, — приоритет. Тип по имени/порядку/расширению и правила на клиенте = снятие.

## Прочитай перед любой задачей
0. **Скилл `../.agents/skills/subsidy-dev/SKILL.md`** — цикл задача → PR и скрипты. Для кода: бэкенд — `backend-spring`, фронт — `frontend-fsd`.
1. `rules.md` — жёсткие правила. Нарушение = риск снятия всей команды.
2. `docs/ARCHITECTURE.md` — сервисы, конвейер (тип → поля → комплектность → правила), стадии, модель данных.
3. `docs/REQUIREMENTS.md` — FR/NFR с ID. В каждом коммите ссылайся на ID требования.
4. `docs/DOC-TYPES.md` — виды документов, поля, чек-лист; `docs/RULES-CATALOG.md` — правила и формулы (источник правды для схем и проверок).
5. `docs/API.md` — контракт, стадии и коды. Сначала контракт, потом код.
6. `docs/DECISIONS.md` — журнал решений. Новое архитектурное решение = новая запись ADR.
7. **`AGENTS.md` модуля** (`services/<svc>/`, `frontend/`) — он важнее этого файла; там же `docs/{PRD,ARCHITECTURE,RULES,TASKS,MEMORY}.md`. Начинай с `MEMORY.md` и своей строки в `TASKS.md`.
8. `docs/WORKFLOW.md` — параллельная работа: draft PR, конфликты, локальный CI.

## Карта папки
```
services/     Gradle Kotlin DSL (один wrapper, version catalog, build-logic), Java 21, Spring Boot 3.5
  build-logic/ gradle/ config/checkstyle/   создаются на Т0: init-backend.sh services (кейс = subsidy)
  libs/common/  ProblemDetail, CurrentUser, outbox, idempotent consumer, конверт событий
  libs/llm/     LlmGateway + адаптеры gigachat/openai/ollama — единственное место с провайдерами (Д6)
  gateway/ package/ docproc/ rules/ assistant/   Java-сервисы (у каждого AGENTS.md + docs/)
  ocr/          Python 3.12 + FastAPI: текстовый слой PDF, OCR, качество скана, таблицы (uv)
contracts/    openapi/<service>.json, events/<topic>/<Type>.schema.json
frontend/     одно Vite-приложение FSD, зона /specialist; AGENTS.md + docs/ (DESIGN.md)
infra/        docker-compose.yml, Caddyfile, keycloak/realm-subsidy.json, ollama/, .env.example
eval/         прогон качества на открытом наборе (сам набор — вне git), см. docs/EVAL.md
docs/         документация (допуск Д10)
scripts/      task-start, ci-local, open-pr, verify, guard, new-migration, gen-env, smoke, install-hooks
```

## Владельцы модулей
| Модуль | Владелец |
|---|---|
| gateway, package, libs/common, build-logic, infra, CI | B1 |
| docproc, ocr (Python), промпты и схемы извлечения, eval | B2 |
| rules, assistant, libs/llm (адаптеры, локальный провайдер) | B3 |
| frontend: страница пакета (загрузка, файлы, просмотр, смена типа, дозагрузка) | F1 |
| frontend: app, shared, entities, очередь, протокол, организация, статистика, помощник | F2 |

## Как работать
- Одна задача (строка TASKS.md) = одна ветка = один worktree = один PR с зелёными проверками.
- Старт: `scripts/task-start.sh <TASK> <FR> <slug>`. Продолжаешь начатую задачу — сначала описание PR и `MEMORY.md` модуля.
- По ходу: тест → код → `scripts/ci-local.sh quick` → коммит → push (каждые 20–40 минут).
- Финиш: `scripts/ci-local.sh full`, статус `review` в TASKS.md, `scripts/open-pr.sh <ID> "<заголовок>"`. Мерж — владелец модуля после зелёного CI и ревью.
- Меняешь контракт — обнови `docs/API.md`, `./gradlew :<svc>:exportOpenApi`, на фронте `npm run gen:api`.
- Меняешь схему поля или вид документа — сначала `docs/DOC-TYPES.md`, затем миграция реестра `doc_types`, затем прогон `eval/` (качество не должно упасть). Меняешь правило — сначала `docs/RULES-CATALOG.md`, затем тест с числами, затем код в `rules`.
- Не придумывай данные, моки и «временные» заглушки в боевом пути (rules.md §1). Не подгоняй под файлы открытого набора.
