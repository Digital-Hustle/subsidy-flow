# PRD — gateway

Продукт целиком — `../../../docs/PRD.md`, требования с ID — `../../../docs/REQUIREMENTS.md`. Здесь — только этот модуль.

## Обзор
Единая точка входа рабочего места специалиста: один origin, проверка JWT на входе, маршрутизация на package, docproc, rules, assistant. Сервисы, ocr, Ollama и Kafka — во внутренней сети (ADR-001).

## Проблемы
- Четыре Java-сервиса — фронту нужен один адрес, CORS и TLS.
- Закрытость (С16): ни один путь без входа, кроме health.
- Большие загрузки и ZIP — лимит тела и rate-limit до сервисов.
- `/internal/**` (выдача расшифрованных файлов) не должен быть доступен снаружи.

## Цели
1. Только gateway открыт наружу; `/internal/**` → 404.
2. JWT Keycloak на входе для всех путей; сервисы всё равно проверяют роли сами.
3. `traceparent`/`X-Request-Id` на каждый запрос.
4. SSE стадий и протокола без буферизации.

## Пользователи
- Фронтенд `/specialist` — через `https://<host>/<svc>/api/v1/**`.
- Судьи — health, Swagger.

## Ключевые функции
- `RewritePath /<svc>/api/v1/** → /api/v1/**` для package, docproc, rules, assistant.
- OAuth2 Resource Server (WebFlux), issuer — realm `subsidy`.
- Публичные без JWT: только health.
- Лимит тела: `UPLOAD_MAX_MB` на `/package/api/v1/packages/*/files**`, остальное 1 МБ.
- Rate-limit загрузок и помощника.
- SSE (`/stream/**`) без таймаута ответа.

## MVP
| Обязательно (P0, к интеграции №1–2) | Потом (P1/P2, если есть время) |
|---|---|
| маршруты, JWT, блок `/internal/**`, CORS, лимит тела, trace | rate-limit, заголовки безопасности (если не Caddy), агрегированный Swagger |

Задачи и статусы — `TASKS.md`.
