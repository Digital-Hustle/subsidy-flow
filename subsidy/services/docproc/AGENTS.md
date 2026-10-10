# AGENTS.md — сервис docproc (Java 21, Spring Boot 3.5) — вид документа и поля

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/docproc/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B2. **Порт:** 8082. **БД:** `docproc`. Самый дорогой блок (25 + 20): очередь `jobs`, сегментация и вид по содержимому, извлечение полей, нормализация, уверенность.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/docproc/
(cd .. && ./gradlew :docproc:check)
(cd .. && ./gradlew :docproc:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
