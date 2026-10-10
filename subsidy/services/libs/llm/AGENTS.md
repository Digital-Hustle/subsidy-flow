# AGENTS.md — библиотека libs/llm (Java 21) — изоляция модели

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/libs/llm/`. Красные линии `../../../rules.md §1` действуют всегда.

**Владелец:** B3. **Порт:** —. **БД:** нет. `LlmGateway` и адаптеры провайдеров; подключается в docproc и assistant. Только здесь знают о провайдерах (Д5, Д6).

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/libs/llm/
(cd ../.. && ./gradlew :libs:llm:check)
(cd ../../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
