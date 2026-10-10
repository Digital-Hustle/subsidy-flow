# TASKS — assistant

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| AS-01 | B-06 | `new-service.sh assistant 8084 B3`, Liquibase: conversations, messages | B3 | done #47 |
| AS-02 | B-06 | Инструменты чтения через `/internal/rules/packages/{id}/facts`, тест доступа | B3 | done #47 |
| AS-03 | B-06 | Цикл tool calling через `LlmGateway.chat` | B3 | done #47 |
| AS-04 | B-06 | `NumberValidator` + тест | B3 | done #47 |
| AS-05 | B-06 | Аномалии в ответе (из `ANOMALY_*`), подсказки вопросов | B3 | done #47 |
