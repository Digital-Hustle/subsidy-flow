# AGENTS.md — сервис package (Java 21, Spring Boot 3.5)

Этот файл важнее `subsidy/AGENTS.md` для всего внутри `services/package/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B1. **Порт:** 8081. **БД:** `package`. Организации, пакеты, приём файлов и ZIP с серверными проверками, версии, хранение (AES-GCM), выдача оригиналов.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, конфликты).

## Команды
```bash
# из services/package/
(cd .. && ./gradlew :package:check)
(cd .. && ./gradlew :package:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
