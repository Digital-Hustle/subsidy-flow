# AGENTS.md — сервис ocr (Python 3.12, FastAPI, Tesseract)

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/ocr/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B2. **Порт:** 8000. **БД:** нет (stateless). Текст, слова с координатами, таблицы и метрики качества из PDF и изображений. **Решений о виде и полях не принимает.**

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` (раздел Python) и процесс `../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/ocr/
uv sync && uv run ruff check . && uv run ruff format --check . && uv run mypy src && uv run pytest -q
uv run uvicorn ocr.main:app --port 8000
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```

## Не клади в git
Документы организаций, файлы открытого набора, traineddata (ставится в образ из пакета).
