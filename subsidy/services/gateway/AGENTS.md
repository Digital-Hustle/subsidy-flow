# AGENTS.md — сервис gateway (Java 21, Spring Cloud Gateway)

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/gateway/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B1. **Порт:** 8080. **БД:** нет. Единственный сервис, доступный снаружи (через Caddy).

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/gateway/
(cd .. && ./gradlew :gateway:check)
(cd .. && ./gradlew :gateway:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
