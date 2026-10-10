# AGENTS.md — сервис assistant (Java 21, Spring Boot 3.5) — помощник по пакету

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/assistant/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B3. **Порт:** 8084. **БД:** `assistant`. Бонус B-06: ответы на вопросы специалиста по данным пакета через инструменты, с валидатором чисел. Ничего не меняет. P2 — делаем после P0/P1.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/assistant/
(cd .. && ./gradlew :assistant:check)
(cd .. && ./gradlew :assistant:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
