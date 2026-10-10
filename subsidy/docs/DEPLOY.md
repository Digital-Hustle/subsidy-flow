# Деплой и CI/CD — «Субсидии»

Схема та же, что у «Проходной»: GHCR-образы, SSH-деплой, health-check и откат. Ниже — отличия.

## Сервер
- **8 vCPU / 32 ГБ RAM / 100 ГБ NVMe**, Ubuntu 24.04 (локальная модель с vision — самое тяжёлое). Без локальной модели (облачный провайдер) хватит 8 vCPU / 16 ГБ. С NVIDIA GPU разбор на локальной модели в 5–10 раз быстрее.
- Домен или `<ip>.sslip.io`; Keycloak — под `https://<host>/auth` (Caddy, `KC_HTTP_RELATIVE_PATH=/auth`). HTTPS обязателен; без домена — `tls internal` (внутренний CA Caddy).
- Текущий стенд команды: `<IP сервера>` (Debian 13, 10 CPU, 20 ГБ RAM, 40 ГБ) — общий с кейсом «Документы»: следить за RAM, локальные модели (~11 ГБ) и стенд «Документов» вместе впритык.
- **Запасной стенд** — ноутбук 32 ГБ с тем же compose и скачанными моделями.

## Контейнеры (лимиты RAM)
| Сервис | RAM |
|---|---|
| caddy, frontend (nginx) | 100 МБ |
| gateway | 384 МБ |
| package, rules | по 512 МБ |
| docproc (воркеры, JSON Schema) | 768 МБ |
| ocr (Tesseract, OpenCV, рендер PDF; 2 процесса uvicorn) | 1 ГБ |
| keycloak | 1 ГБ |
| postgres (БД на сервис + keycloak, файлы в bytea) | 1 ГБ |
| kafka (KRaft, 1 брокер) | 1 ГБ |
| jaeger | 256 МБ |
| ollama: `qwen3:8b` + `qwen2.5vl:7b` (профиль `llm-local`) | 12 ГБ |
| **Итого** | **≈ 19–20 ГБ** (с моделью по API — ≈ 7–8 ГБ, ollama не нужен) |

## Подготовка сервера (B1, один раз)
1. Пользователь `deploy`, SSH-ключ, `ufw allow 22,80,443`; Docker + compose plugin.
2. `git clone` в `/opt/subsidy-flow` (нужна `subsidy/infra/`), `subsidy/scripts/gen-env.sh` на сервере, вписать хост и ключи моделей.
3. `docker login ghcr.io` (read-only токен); `scripts/smoke.sh https://<host>` после запуска:
   - модель локально: `LLM_PROVIDER=ollama`, `docker compose --profile llm-local up -d`;
   - модель по API (OpenAI-совместимый шлюз): `LLM_PROVIDER=openai` + `LLM_BASE_URL`/`LLM_MODEL`/`LLM_VISION_MODEL`/`LLM_API_KEY`/`LLM_JSON_MODE` в `infra/.env` (пример — `docs/LLM.md`), `docker compose up -d` без профиля.
   Переключение — правка `.env` и `docker compose up -d docproc assistant`, пересборка не нужна.

## Текущий стенд
https://subsidy.digital-hustle.ru (сервер <IP сервера>, за общим edge, порт кейса 127.0.0.1:8280; копия репо `/root/stand`).
Модель — по API (`LLM_PROVIDER=openai`, `deepseek-v4-flash` + `qwen3-vl-plus`, `LLM_JSON_MODE=json_object`), ollama не запущен.
Обновление: `cd /root/stand && git pull && cd subsidy/infra && docker compose build <svc> && docker compose up -d` →
`EVAL_PASSWORD=<KC_DEMO_SPECIALIST_PASSWORD> scripts/smoke.sh https://subsidy.digital-hustle.ru`. Учётки демо — в `infra/.env` (`KC_DEMO_*`).
Realm импортируется только в пустую БД Keycloak: на работающем стенде новые настройки realm (тема входа `subsidy`, «запомнить меня») ставит
`docker compose exec keycloak /opt/keycloak/bin/kcadm.sh update realms/subsidy -s loginTheme=subsidy -s rememberMe=true --server http://localhost:8080/auth --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"` (после `up -d keycloak` с примонтированной темой).

## Режим стенда и вход сотрудников (ADR-023)
`STAND_MODE` в `infra/.env`: `demo` — демо-сотрудники (`specialist|senior|auditor@demo.subsidy.local`), код `000000` и код входа прямо на странице; `prod` — демо-сотрудники отключаются при старте package, код только письмом. Переключение: правка `.env` → `docker compose up -d package`.
Обязательны `STAFF_TOKEN_SIGNING_KEY` (ES256, генерирует `scripts/gen-env.sh`) и `STAFF_CODE_PEPPER`. Первый живой старший специалист заводится из `STAFF_BOOTSTRAP_EMAIL` при старте package, дальше — приглашения в разделе «Сотрудники».
Почта — общий ящик всех кейсов: `cp infra/mail.env.example infra/mail.env` и заполнить `SMTP_*` (или указать общий файл сервера через `MAIL_ENV_FILE=/root/shared/mail.env`); корневой [MAIL.md](../../MAIL.md). Без SMTP письма не уходят — вход работает только в демо-режиме (код виден в ответе API; `STAFF_LOG_CODES=true` пишет его в лог package).

## За общим edge (сервер на несколько кейсов)
Порты 80/443 держит общий Caddy (`edge/`, TLS Let's Encrypt, маршрут по имени хоста). Кейс поднимается с
`CADDY_SCHEME=http://`, `HTTP_BIND=127.0.0.1:8280`, `HTTPS_BIND=127.0.0.1:8643`, `PUBLIC_HOST=KC_HOSTNAME=subsidy.<домен>`,
`OIDC_ISSUER_URI=https://subsidy.<домен>/auth/realms/subsidy` — его Caddy слушает только http, `X-Forwarded-*` от edge доверены
(`trusted_proxies private_ranges`). Без этих переменных кейс как раньше сам на 80/443 (внутренний CA, `local_certs`).

## CI на сервере (основной, ADR-017)
Если GitHub Actions недоступны, те же шаги гоняет скрипт на сервере (по одному прогону за раз, своя БД Postgres) и ставит commit status `server-ci`. Мерж — при зелёном `server-ci`. Процесс — `docs/WORKFLOW.md`.

## CI (`.github/workflows/subsidy-ci.yml`, когда Actions снова доступны)
На PR и push в `main` с изменениями `subsidy/**`: changes → services (матрица изменённых: Java — Postgres service, Liquibase, jOOQ, `check`, be-check, контракт; Python ocr — ruff, mypy, pytest) → frontend (`gen:api`, токены, lint, typecheck, build, fsd-check) → eval (синтетика, если есть `eval/run.py`) → security (gitleaks по истории, `subsidy/scripts/guard.sh`, Trivy) → infra (`compose config`) → smoke (только `main`).

## CD (`.github/workflows/subsidy-deploy.yml`)
push в `main` → CI зелёный → jar-ы → образы `ghcr.io/<org>/subs-<svc>:<sha>` → SSH: `docker compose pull && up -d` → health-check → откат на прошлый тег. Включение: переменная репозитория `SUBS_DEPLOY_ENABLED=true` + секреты `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. Секретов приложения в GitHub нет.

## Перед защитой
Тег `subs-demo-final`, деплой тега, заморозка деплоя; маршрут судьи на сервере и на запасном ноутбуке; модели и образы — локально (`docker save` в архив).
