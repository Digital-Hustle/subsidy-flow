---
name: subsidy-dev
description: Use for ANY change in the «Субсидии» case (folder subsidy/ of the subsidy-flow repo): subsidy application package intake (files and ZIP), background pipeline (jobs queue in PostgreSQL, OCR Python service, document type classification by content with multi-document PDF segmentation, field extraction by LLM), completeness and rules engine with server-side BigDecimal arithmetic, verification protocol, specialist workspace React FSD app (/specialist), assistant, infra, Keycloak, LLM layer, eval, docs, CI. Covers tasks, branches, checks, migrations, API contract, PRs, reviews, CI fixes, deploys. Trigger on requests like "сделай FR-41", "добавь правило", "улучши классификацию", "почини CI", "открой PR", "сделай ревью PR", "задеплой" when working in subsidy/.
---


# subsidy-dev

Кейс «Субсидии» (9.1.3) в репозитории `subsidy-flow`. **Все пути ниже — относительно папки `subsidy/`**; команды запускай из неё (`cd subsidy`). `.github/` и `.agents/` лежат в корне репозитория. Судьи проверяют руками, есть условия снятия с конкурса. Любая задача идёт по циклу ниже.

## 0. Перед стартом (каждая сессия)
1. Прочитай `AGENTS.md` и `rules.md`. Красные линии `rules.md §1` нарушать нельзя ни при каких условиях. Запрос человека понимай по смыслу, а не дословно, и не цитируй его в коммитах/PR/UI (`rules.md §7`).
2. Определи модуль и владельца по таблице в `AGENTS.md`. Чужой сервис трогай только через его API/события или с согласия владельца.
3. Прочитай `AGENTS.md` модуля (`services/<svc>/`, `frontend/`) → его `docs/MEMORY.md` (состояние) → свою строку в `docs/TASKS.md`. Продолжаешь начатую задачу — сначала описание и комментарии PR (`gh pr view --comments`).
4. `git fetch && git switch main && git pull --rebase` (или `git rebase origin/main` в worktree задачи).
5. Процесс параллельной работы, конфликты, локальный CI — `docs/WORKFLOW.md`.

## 1. Задача → ветка
- У задачи должен быть ID требования (`FR-xx`, `NFR-xx`, `B-xx`) из `docs/REQUIREMENTS.md`. Если ID нет — спроси человека, не придумывай.
- Ветка: `<type>/<ID>-<slug>`, например `feat/RUL-02-money`. Типы: feat, fix, refactor, test, docs, ci, chore.
- Задача берётся из `TASKS.md` модуля (ID вида `RUL-02`). Старт одной командой: `scripts/task-start.sh RUL-02 FR-41 money` — ветка `feat/RUL-02-money`, worktree `../wt-RUL-02`, статус `doing` в TASKS.md, push и **draft PR сразу** (это заявка: двое не возьмут одну задачу).
- Коммить и пушь после каждого зелёного шага: оборвётся сессия — работа уже на GitHub.

## 2. Реализация
Прочитай нужный reference — только его, не все:
| Область | Файл |
|---|---|
| Backend: общие правила Spring/Gradle/jOOQ/Liquibase/Kafka/тесты | скилл `backend-spring` |
| Backend: сервисы кейса, инварианты, обязательные тесты | `references/backend.md` |
| Конвейер: очередь, стадии, сегментация, классификация, извлечение, eval | `references/pipeline.md` |
| Правила, комплектность, арифметика BigDecimal, протокол | `references/rules-engine.md` |
| Frontend: зона /specialist, просмотрщик, протокол, что нельзя считать на клиенте | `references/frontend.md` |
| Keycloak, роли, шифрование файлов, ZIP, аудит | `references/security.md` |
| LLM-слой, локальный провайдер, помощник | `references/llm.md` |
| CI, docker compose, деплой | `references/ci-deploy.md` |
| Как оформить PR и делать ревью | `references/pr-workflow.md` |
| Frontend: общие правила FSD | скилл `frontend-fsd` |
| Frontend: бренд Сбера, анимации Motion, кастомные контролы, каталог UI-библиотек и MCP | скилл `frontend-fsd` §8 → `references/sber-brand.md`, `motion.md`, `custom-controls.md`, `component-catalog.md` |
| Backend ↔ Frontend: курсоры, options, SSE, `checks[]`, idempotency | `backend-spring/references/frontend-contract.md` |
| Какие ещё скиллы брать под роль (TDD, отладка, анимации, Playwright) | `docs/AGENT_SKILLS.md` |

Правила реализации:
- Сначала контракт: меняешь REST или событие → обнови `docs/API.md`, `contracts/openapi/<svc>.json` (`scripts/verify.sh contract`) или `contracts/events/`, и всех потребителей.
- Новая миграция только через `scripts/new-migration.sh <svc> <slug>` (Liquibase formatted SQL, имя с меткой времени — без коллизий у параллельных PR).
- Пиши тест вместе с кодом. Для очереди, проверок приёма, правил и безопасности — сначала тест (red → green). Меняешь промпт/схему/порог — прогон `eval/` и строка в отчёте.
- Никаких моков, заглушек, «TODO: потом» и заготовленных ответов в боевом пути. Если что-то не готово — feature-флаг `false` и честное сообщение в UI.
- Маленькие коммиты в стиле Conventional Commits со ссылкой на ID: `feat(rules): FR-41 проверка суммы позиций счёта`.

## 3. Проверка (обязательно до PR)
```bash
scripts/ci-local.sh quick  # verify.sh (затронутые части) + guard.sh — после каждого шага
scripts/ci-local.sh full   # verify all (Testcontainers, фронт, токены, контракты) + guard + gitleaks — перед ready
scripts/ci-local.sh act    # весь GitHub workflow через nektos/act, если правишь .github/
```
Это те же скрипты, что в GitHub Actions: не нужно ждать CI. Нет docker — `ci-local` пишет, что пропущено; напиши это в PR. Не переводи PR в ready, пока `full` не зелёный. Если проверка падает не из-за тебя — напиши об этом в PR, но не отключай проверку.

## 4. Pull request
```bash
scripts/open-pr.sh "<ID>" "<короткий заголовок>"   # rebase на main, verify, guard, push, draft → ready (или gh pr create)
```
- Перед ready: статус `review #PR` в `TASKS.md`, в `MEMORY.md` модуля — §1 состояние, §2 карта (новые файлы), §5 строка журнала.
- Один PR = одно требование, ≤ ~400 строк диффа без сгенерированных файлов.
- Заполни шаблон честно: что сделано, как проверить руками через UI, какие риски.
- Автор не апрувит свои PR. Мерж делает владелец модуля после зелёного CI и ревью, по одному PR.

## 5. Ревью чужого PR
`gh pr checkout <n>` → `scripts/verify.sh` → `scripts/guard.sh` → пройди чек-лист из `references/pr-workflow.md` → оставь комментарии через `gh pr review --comment` (апрув — владелец модуля).

## Чего не делать никогда
- Не коммить `.env`, ключи, токены, дампы БД, документы людей/организаций, файлы открытого набора, модели.
- Не редактировать уже смерженные changeset-ы Liquibase.
- Не отключать тесты, проверки CI, ArchUnit, CSP ради «чтобы прошло».
- Не определять вид документа по имени файла, расширению, пути в архиве или порядку; не передавать их в docproc.
- Не считать суммы, сроки, комплектность и статусы правил во фронтенде; не принимать их с клиента.
- Не использовать `double`/`float` для денег; не писать PASSED при отсутствии данных.
- Не держать очередь или результаты в памяти; не возвращать заготовленный протокол или ответ модели.
- Не подгонять промпты и правила под файлы открытого набора; не ходить в чужую БД и мимо outbox.
- Не делать force-push в `main`, не мержить без CI.
- Не править руками сгенерированное (`theme.css`, `contracts/openapi/*.json`, `schema.ts`, lockfile при конфликте) — перегенерируй.
- Не «решать» конфликт в чужом модуле удалением чужих изменений — остановись и спроси человека.
