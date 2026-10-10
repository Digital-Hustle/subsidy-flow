# Frontend — специфика «Субсидий»

Общие правила FSD — **скилл `frontend-fsd`**; модуль — `frontend/AGENTS.md`, `frontend/docs/`.

- Одно приложение `frontend/`, зона `/specialist` (desktop). F1 — страница пакета (загрузка, файлы/документы, просмотрщик, поля, смена типа, правка, дозагрузка), F2 — каркас, очередь, протокол, аналитика, помощник.
- ⛔ Клиент **не считает** суммы, НДС, сроки, комплектность и статусы — только показывает ответ rules; деньги приходят строками, `shared/lib/money.ts` только форматирует (`guard.sh` ищет `reduce` по суммам).
- ⛔ Тип документа не выводится из имени файла даже как подсказка.
- Правка поля / смена типа / замена файла → мутация → ждём SSE `package.checked` → перечитываем протокол.
- Оригинал — blob через `/package/api/v1/files/{id}/content`; bbox из результата docproc; клик по `evidence` в протоколе → документ и поле.
- Каждый экран: loading / empty / error / offline; пустая БД → нули.

```bash
cd frontend && npm ci && npm run gen:api && npm run dev
npm run lint && npm run typecheck && npm run build && node ../../.agents/skills/frontend-fsd/scripts/fsd-check.mjs src
```
