# AGENTS.md — сервис rules (Java 21, Spring Boot 3.5) — комплектность, правила, протокол

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/rules/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B3. **Порт:** 8083. **БД:** `rules`. Комплектность (15) и правила с арифметикой (часть 20) — **только здесь, на сервере** (Д11). Протокол, выгрузка, история, статистика, истекающие.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/rules/
(cd .. && ./gradlew :rules:check)
(cd .. && ./gradlew :rules:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
