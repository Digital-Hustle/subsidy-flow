# TASKS — gateway

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #74` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| GW-01 | NFR-15 | `new-service.sh gateway 8080 B1 --gateway`, health, Dockerfile | B1 | done |
| GW-02 | NFR-15 | Маршруты package/docproc/rules/assistant, SSE без буферизации | B1 | done |
| GW-03 | FR-66 | JWT Keycloak на всех путях, тест «без токена → 401» | B1 | done |
| GW-04 | NFR-08 | Блок `/internal/**`, CORS, лимит тела по маршрутам | B1 | done #74 (CORS не нужен: фронт и API на одном origin за Caddy) |
| GW-05 | FR-03 | Rate-limit загрузок и помощника, 429 + Retry-After | B1 | done #74 |
