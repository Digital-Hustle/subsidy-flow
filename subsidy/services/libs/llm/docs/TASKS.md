# TASKS — libs/llm

Формат — `../../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| LLM-01 | FR-50 | Каркас, `LlmGateway`, исключения, автоконфигурация, `LlmIsolationTest` | B3 | done |
| LLM-02 | FR-50 | `GigaChatAdapter` + WireMock | B3 | done |
| LLM-03 | FR-50 | `vision` и tool calling (assistant) | B3 | todo |
| LLM-04 | FR-50 | Resilience + `SchemaValidator` + перегенерация | B3 | done |
| LLM-05 | FR-53/56 | `LlmCallJournal`, метрики, `status()` | B3 | done |
| LLM-06 | FR-51 | `OllamaAdapter` + `OpenAiCompatAdapter`; Ollama и `ollama-pull` в compose (с B1) | B3 | done |
| LLM-07 | FR-51 | `infra/ollama/README.md`: офлайн-архив, инструкция для судей | B3 | todo |
| LLM-08 | FR-52 | Замер моделей (с B2) → LLM.md | B3 | todo |
