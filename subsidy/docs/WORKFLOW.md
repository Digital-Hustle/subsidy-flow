# Как команда работает параллельно (ADR-013 ← P-ADR-035)

Коротко: **одна задача → одна ветка → один worktree → один draft PR**. Состояние задачи живёт в git (PR, `TASKS.md`, `MEMORY.md`), а не в чатах. Проверки одни и те же локально и в GitHub.

## 1. Где что лежит (иерархия контекста)
Ближайший к файлам `AGENTS.md` важнее корневого (Codex, Cursor и Claude Code читают его сами, `CLAUDE.md → @AGENTS.md`). Каждый `AGENTS.md` короткий и ссылается на подробности (progressive disclosure).

| Уровень | Файлы | Кто правит |
|---|---|---|
| Кейс | `AGENTS.md`, `rules.md`, `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/DECISIONS.md`, `docs/TASKS.md`, `docs/MEMORY.md`, этот файл | B1 (капитан); ADR — автор решения |
| Модуль | `services/<svc>/AGENTS.md` + `docs/{PRD,ARCHITECTURE,RULES,TASKS,MEMORY}.md`; `frontend/AGENTS.md` + `docs/{…,DESIGN}.md` | владелец модуля (таблица в `AGENTS.md`) |
| Код | контракты `contracts/`, токены `frontend/src/shared/config/tokens.ts` | владелец + ревью потребителей (CODEOWNERS) |

Документ модуля не повторяет общие: он ссылается на них и описывает только своё.

## 2. Жизненный цикл задачи
1. **Взять задачу.** Строка в `services/<svc>/docs/TASKS.md` (или `frontend/docs/TASKS.md`) со статусом `todo`, своя роль. Чужую — только с согласия владельца.
2. **Старт:** `scripts/task-start.sh <TASK> <FR> <slug>` — свежий `origin/main`, ветка `<type>/<TASK>-<slug>`, отдельный worktree `../wt-<TASK>`, первый коммит «статус doing» в TASKS.md, push и **draft PR** сразу. Открытые draft PR видны всем (`gh pr list --draft`), двое не возьмут одну задачу.
3. **Работа маленькими шагами.** Тест → код → `scripts/ci-local.sh quick` → коммит → push. Коммит каждые 20–40 минут или после каждого зелёного шага.
4. **Готово:** `scripts/ci-local.sh full` зелёный → TASKS: `review #PR` → MEMORY: состояние и карта, если появились новые файлы → `scripts/open-pr.sh <FR> "<заголовок>"` (rebase, verify, guard, push, перевод из draft в ready).
5. **Ревью** — не автор: по `references/pr-workflow.md` скилла `subsidy-dev` + владелец модуля.
6. **Мерж** — владелец модуля (для общих частей — B1) после зелёного CI и ревью. По одному PR за раз; остальные PR после этого делают rebase.

Статусы в TASKS.md: `todo` → `doing #PR` → `review #PR` → `done #PR` (или `blocked: <чем>`). Статус меняется **в той же ветке, что и код**, поэтому в main он попадает вместе с результатом.

## 3. Продолжить начатую задачу
```bash
cd ../wt-<TASK> || git worktree add ../wt-<TASK> <branch>
git fetch && git rebase origin/main
gh pr view --comments            # описание PR и замечания ревью
git log --oneline origin/main..  # что уже сделано
cat services/<svc>/docs/MEMORY.md
```
Дальше — с шага 3 цикла. Сначала описание PR и MEMORY, потом код; ничего не переписываем «с нуля», не поняв, почему сделано так.

## 4. Согласованность между людьми
**Бэк ↔ бэк** (B1, B2, B3):
- Контракт первым: синхронный API — `docs/API.md` + `contracts/openapi/<svc>.json` (генерируется `./gradlew exportOpenApi`), события — `contracts/events/<topic>/<Type>.schema.json`. Изменение контракта — **отдельный маленький PR** `contract/<TASK>-…`, ревью владельцев всех потребителей (CODEOWNERS). Потребители адаптируются следующими PR.
- Несовместимое изменение события = новый топик `.v2`, старый живёт до перехода всех потребителей.
- `libs/common`, `build-logic`, `gradle/libs.versions.toml` — владелец B1. Нужна правка — попроси B1 или открой PR с ревью B1; не копируй общий код в сервис.
- Миграции только `scripts/new-migration.sh` (имя с меткой времени — два PR не столкнутся). Смерженные changeset-ы не трогаем.
- Внутренние вызовы — через `ServiceClients` и `/internal/**`, чужую БД не читаем (guard.sh ловит).

**Фронт ↔ фронт** (F1, F2):
- `app`, `shared`, `entities`, `tokens.ts`, `package.json` — владелец F2. F1 добавляет зависимость или общий компонент через PR с ревью F2 (или просит F2).
- Типы API — только из `npm run gen:api`, руками DTO не пишем. Новый общий UI-контрол — в `shared/ui` одним PR, потом используют обе зоны.
- Цвета, шрифты, отступы, анимации — только из токенов (раздел 7, `frontend/docs/DESIGN.md`).

**Фронт ↔ бэк**: формат ответа и коды ошибок — по `docs/API.md` и `backend-spring/references/frontend-contract.md`; изменение — PR в контракт с ревью F-владельца зоны.

## 5. Конфликты
- **Предотвращаем:** маленькие PR (≤ ~400 строк), rebase на `origin/main` в начале каждой сессии и перед ready, свой модуль — свои файлы, общие файлы — через владельца.
- **Сгенерированные файлы не мержим руками, а перегенерируем**: `package-lock.json` (`git checkout --theirs package-lock.json && npm install`), `contracts/openapi/*.json` (`./gradlew exportOpenApi`), `src/app/styles/theme.css` и таблицы DESIGN.md (`npm run gen:tokens`), `src/shared/api/schema.ts` (`npm run gen:api`).
- **TASKS.md / MEMORY.md**: журнал в MEMORY — только дописываем строки в конец; `.gitattributes` включает `merge=union` для `MEMORY.md`, поэтому параллельные записи склеиваются без конфликта. В TASKS каждый правит только свои строки.
- **Конфликт в чужом коде** — останавливаемся и зовём владельца; не «решаем» конфликт удалением чужих изменений.
- **Семантический конфликт** (оба PR зелёные, вместе ломаются) ловит CI на `main` после каждого мержа; поэтому мержим по одному и после мержа остальные делают rebase + `ci-local.sh quick`.
- Спор о решении → владелец модуля решает в своём модуле; межмодульный — B1, итог записывается ADR.

## 6. Локальный CI (не ждём GitHub)
`scripts/ci-local.sh` гоняет те же проверки, что GitHub Actions, теми же скриптами:

| Режим | Что | Когда |
|---|---|---|
| `quick` | `verify.sh` по изменённым частям + `guard.sh` | после каждого шага |
| `full` (по умолчанию) | `verify.sh all` (Gradle `check` с Testcontainers Postgres/Kafka, Python ruff/mypy/pytest, фронт lint/typecheck/build/fsd-check, токены, контракты), `guard.sh`, gitleaks (бинарник или docker) | перед ready |
| `act` | весь `.github/workflows/subsidy-ci.yml` через [nektos/act](https://github.com/nektos/act) (MIT) в docker | если правишь сам CI |

Нужен docker (Testcontainers, gitleaks в контейнере). Если docker нет — `ci-local.sh` пишет, какие шаги пропущены; это указывается в PR («интеграционные тесты локально не прогнаны»), и тогда решает CI GitHub. Отключать проверки, чтобы «прошло», нельзя (rules.md).

## 7. Единый источник дизайн-токенов (ADR-012 ← P-ADR-033)
`frontend/src/shared/config/tokens.ts` → `npm run gen:tokens` → `src/app/styles/theme.css` (Tailwind 4 + shadcn) и таблицы в `frontend/docs/DESIGN.md`; Motion берёт значения через `shared/config/motion.ts`. Хардкод цветов, произвольных значений Tailwind, отступов вне сетки и чисел в анимациях ловит `fsd-check`, устаревшую тему — `verify.sh frontend` (`gen-tokens --check`).

## 8. Роли и шаги — `docs/ROLES.md`
Там: кто за что отвечает, что каждому нужно от других (need) и к какому времени, и порядок шагов на день хакатона.
