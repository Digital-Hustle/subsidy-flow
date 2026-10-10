# PRD — libs/llm

Продукт целиком — `../../../../docs/PRD.md`, требования с ID — `../../../../docs/REQUIREMENTS.md`. Здесь — только этот модуль.

## Обзор
Единый интерфейс `LlmGateway` (`chat` с инструментами, `structured(schema)`, `vision`, `status`), адаптеры GigaChat, OpenAI-совместимый, Ollama; выбор настройкой; таймаут/повтор/CB/семафор; валидация JSON Schema; журнал вызовов. Даёт 15 баллов «модель и локальный запуск».

## Проблемы
- Судьи проверяют изоляцию модели и смену провайдера настройкой.
- Локальный провайдер должен работать без интернета.
- Модели отвечают невалидным JSON и падают.

## Цели
1. Смена провайдера — только `LLM_PROVIDER`.
2. `structured` валиден по схеме или понятное исключение.
3. Каждый вызов — в журнале сервиса.
4. ArchUnit: провайдеры только в `ru.subsidy.llm.infra`.

## Пользователи
- docproc (сегментация, классификация, извлечение).
- assistant (tool calling).

## Ключевые функции
- `LlmGateway`, `LlmException` (Unavailable/Timeout/InvalidOutput/Misconfigured).
- `OllamaAdapter`, `OpenAiCompatAdapter`, `GigaChatAdapter` — HTTP без SDK.
- Resilience4j, семафор `LLM_MAX_CONCURRENCY`.
- `SchemaValidator` + перегенерация.
- Tool calling для assistant (`chat(messages, tools)`).
- Метрики Micrometer.

## MVP
| Обязательно (P0, к интеграции №1–2) | Потом (P1/P2, если есть время) |
|---|---|
| интерфейс, Ollama + OpenAI-совместимый, structured, resilience, журнал, ArchUnit | GigaChat, vision, tool calling, замер моделей |

Задачи и статусы — `TASKS.md`.
