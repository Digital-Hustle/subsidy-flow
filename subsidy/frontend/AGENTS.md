# AGENTS.md — frontend (одно Vite-приложение, FSD, зона /specialist)

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `frontend/`. Красные линии `../rules.md §1` действуют всегда.

**Владельцы:** F1 — страница пакета (`pages/PackagePage`, виджеты `FileList`, `DocumentViewer`, `FieldsPanel`, фичи загрузки, смены типа, правки, дозагрузки), F2 — `app`, `shared`, `entities`, `tokens.ts`, `package.json`, очередь, протокол, организация, статистика, помощник.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, известные баги. Потом `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя во фронте.
3. Любая визуальная работа → **`docs/DESIGN.md`** (токены, компоненты, отступы). Никаких цветов/размеров/длительностей литералами.
4. Структура и потоки — `docs/ARCHITECTURE.md`; зачем и для кого — `docs/PRD.md`.
5. Скилл `../../.agents/skills/frontend-fsd/SKILL.md` и процесс `../docs/WORKFLOW.md` (draft PR).

## Команды
```bash
npm ci && npm run gen:api && npm run gen:tokens
npm run dev                                  # https://localhost:5173/specialist
npm run lint && npm run typecheck && npm run build
node ../../.agents/skills/frontend-fsd/scripts/fsd-check.mjs src
../scripts/ci-local.sh quick                 # то же, что CI, из subsidy/
```

## Не делай
- ⛔ Не считай на клиенте суммы, сроки, комплектность и статусы правил (Д11, снятие) — только показывай ответ `rules`.
- ⛔ Не выводи тип документа из имени файла (даже как подсказку «похоже на счёт»).
- Не храни токены в localStorage, не подключай моки вне `import.meta.env.DEV`.
- Не правь `src/app/styles/theme.css` руками — он генерируется из `src/shared/config/tokens.ts`.
- Не удаляй `docs/`, `AGENTS.md`, `CLAUDE.md` при скаффолдинге.
