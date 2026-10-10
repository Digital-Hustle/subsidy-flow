# TASKS — межмодульные задачи и вехи (владелец B1)

Задачи модулей — в их `TASKS.md`:
[gateway](../services/gateway/docs/TASKS.md) · [package](../services/package/docs/TASKS.md) · [docproc](../services/docproc/docs/TASKS.md) · [ocr](../services/ocr/docs/TASKS.md) · [rules](../services/rules/docs/TASKS.md) · [assistant](../services/assistant/docs/TASKS.md) · [libs/llm](../services/libs/llm/docs/TASKS.md) · [frontend](../frontend/docs/TASKS.md).
Здесь — платформа (B1) и задачи для двоих и больше. Формат и статусы — `WORKFLOW.md §2`. Кто что делает сейчас — открытые draft PR (`gh pr list --draft`).

## Вехи (PLAN.md)
| Веха | Когда | Условие готовности | Статус |
|---|---|---|---|
| M0 Каркас | Т+0:45 | `init-backend.sh services` + сервисы собираются, compose поднимает pg/kafka/keycloak/ollama, фронт-каркас с токенами, CI зелёный | done (CI — на сервере, GitHub Actions заблокированы биллингом) |
| M1 Контракты | Т+1:00 | API.md, `/internal/**`, `contracts/events/`, схемы 8 видов, формат протокола согласованы | done #78 |
| M2 Интеграция №1, тег `subs-demo-1` | Т+3:00 | пакет → загрузка → стадии → тип и поля моделью → комплектность и 3+ правила → протокол на экране; рестарт docproc посреди разбора — без потерь | todo |
| M3 Интеграция №2, тег `subs-demo-2` | Т+5:00 | 8 видов на открытом наборе (отчёт eval), сегментация склеек, все P0-правила, смена типа и дозагрузка → перепроверка, закрытость по чек-листу I; код-фриз P0/P1 | todo |
| M4 Финал, тег `subs-demo-final` | Т+6:30 | docs, DECISIONS, EVAL-отчёт, слайды, 2 репетиции | todo |

## Платформа (B1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PLT-00 | — | Получить текст кейса: сверить С1–С21, разд. 5.3/5.5 с REQUIREMENTS, DOC-TYPES, RULES-CATALOG, DEMO; поправить гипотезы | B1 + все | частично: Положение (КЕЙС 3 — допуск Д1–Д12, баллы, бонусы, п. 7.4) сверено, DEMO.md — чек-лист допуска; тексты С1–С21 и разд. 5.3/5.5 кейса в Положение не входят — ждём от организаторов |
| PLT-01 | NFR-15 | `init-backend.sh services` (кейс subsidy), `libs/common` — общий каркас команды, каркас `libs/llm` | B1 | done |
| PLT-02 | NFR-01/02 | `infra/docker-compose.yml`: postgres (БД+роль на сервис, volume), kafka + `topics.sh`, keycloak (realm из файла), jaeger, caddy, ollama + ollama-pull (профиль `llm-local`) | B1 | done |
| PLT-03 | NFR-13 | CI `subsidy-ci.yml` уже есть — проверить на первом коде; включить деплой (`SUBS_DEPLOY_ENABLED`) | B1 | done |
| PLT-04 | — | Синк контрактов: API.md, `/internal/**`, `contracts/events/*.schema.json`, стадии, коды правил | B1 + все | done #78 |
| PLT-05 | FR-60 | Keycloak realm `subsidy`: клиент, роли, TOTP, brute-force, демо-пользователи из `.env` | B1 | done (`infra/keycloak/gen_realm.py`) |
| PLT-06 | — | Service accounts `svc-package/docproc/rules/assistant`, `aud` на каждый сервис | B1 | done (`infra/keycloak/gen_realm.py`) |
| PLT-07 | NFR-17 | Outbox relay + IdempotentConsumer + конверт события в `libs/common` | B1 | done |
| PLT-08 | NFR-01 | README «с чистого клона», `smoke.sh`, проверка на чистой машине | B1 | done #79 #98 #102 #104: стенд https://subsidy.digital-hustle.ru за общим edge собран с main, `smoke.sh` со сквозным прогоном — все ✔ (8/8 видов, расхождений 0); по пути исправлены /internal снаружи, healthcheck, HTTP/1.1 к ocr |
| PLT-09 | FR-28 | `eval/`: `run.py` через публичный API, разметка, отчёт; синтетика `eval/synthetic/` (генератор пакетов с корректными реквизитами) + запуск в CI | B2 | done #70 |
| PLT-10 | FR-06 | Единый problem+json для отказов фреймворка: 401/403 из Spring Security, 400 валидации/параметров, 413 размера — с `code` и `hint` (`libs/common/web`) | B1 | done |

## Совместные
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| X-01 | FR-03/62 | Контракт загрузки и стадий: коды отказов, SSE-события, тексты | B1 + B2 + F1 | todo |
| X-02 | FR-33/63 | Формат результата: поля, `bbox`, уверенность, `checks[]`, сегменты — для просмотрщика | B2 + F1 | todo |
| X-03 | FR-45/67 | Формат протокола: статусы, `evidence`, ожидаемое/фактическое — для экрана и выгрузки | B3 + F2 | todo |
| X-04 | FR-52 | Замер моделей на открытом наборе → таблица в LLM.md, выбор локальной модели | B2 + B3 | partial: API-модели замерены на синтетике (LLM.md), выбран deepseek-v4-flash; локальные и открытый набор — после DP-15 |
| X-05 | — | «Злоумышленник» по чек-листу I (каждые 2 часа, по очереди) | все | todo |
| X-06 | NFR-12 | Слайды, матмодель (уверенность, сегментация, формулы правил), репетиции | F2 + B2 + B3 | todo |
