# PR workflow

## Автор
1. `scripts/task-start.sh <TASK> <FR> <slug>` — ветка от свежего `main`, worktree, draft PR сразу (docs/WORKFLOW.md §2).
2. По ходу `scripts/ci-local.sh quick`, коммит + push.
3. Финиш: `scripts/ci-local.sh full` зелёный, TASKS/MEMORY обновлены, `scripts/open-pr.sh <ID> "<заголовок>"` — draft → ready; заполни разделы шаблона.
4. Заголовок PR = Conventional Commit: `feat(rules): FR-41 проверка суммы позиций счёта` (станет сообщением squash-коммита).
5. Конфликт с `main` → `git fetch && git rebase origin/main`, конфликты в миграциях не правь чужие файлы — переименуй свою миграцию новой меткой.
6. Конфликт в `contracts/openapi.json` → перегенерируй (`scripts/verify.sh contract`), не правь руками. То же для `theme.css`/DESIGN.md (`npm run gen:tokens`) и `package-lock.json` (`git checkout --theirs package-lock.json && npm install`).
7. Конфликт в `MEMORY.md` не возникает (`merge=union`); в `TASKS.md` — оставь обе правки своих строк, чужие не трогай.

## Ревьюер (владелец модуля, не автор PR)
`gh pr checkout <n>` → читает PR и `MEMORY.md` модуля → `scripts/ci-local.sh full` → чек-лист ниже → `gh pr review --comment`. Не апрувит и не пушит в чужую ветку без просьбы.
Чек-лист:
- [ ] Красные линии `rules.md §1` не нарушены (guard.sh зелёный, но смотри глазами: тип по имени файла, проверки/правила на клиенте, заготовки, in-memory)
- [ ] Границы модулей соблюдены, ArchUnit не ослаблен
- [ ] Есть тест на новое поведение; тесты осмысленные, а не `assertTrue(true)`
- [ ] Ошибки — коды из API.md, тексты по-русски
- [ ] UI: loading / empty / error / offline
- [ ] Новые зависимости: лицензия разрешённая, запись в DECISIONS.md
- [ ] Миграция новая, не правит старые
- [ ] Изменения промптов/порогов — с отчётом `eval/` (не хуже)
- [ ] Автор (человек) может объяснить код за 60 с
- [ ] Docs/ADR обновлены, если поменялось поведение

Вердикт: `gh pr review <n> --comment -b "..."`. Approve и merge делает человек.
