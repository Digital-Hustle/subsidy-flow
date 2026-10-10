# Субсидии — автоматическая проверка пакета документов на получение субсидии

Хакатон «СберБизнесВайб», кейс 9.1.3. Интерфейс один — **рабочее место специалиста** (`/specialist`, вход через Keycloak). Специалист загружает пакет документов организации одним архивом или файлами в любом порядке и с любыми именами; сервер принимает его мгновенно и в фоне: достаёт текст (PDF-слой или OCR) → **по содержимому** определяет вид каждого документа (и делит многодокументные PDF) → извлекает поля по схеме вида → проверяет комплектность по чек-листу программы → прогоняет правила (ИНН/ОГРН, даты и сроки, **арифметика сумм на сервере**) → формирует протокол проверки. Специалист видит оригинал рядом с результатом, может поправить тип, дозагрузить файл и выгрузить протокол. Архитектура повторяет «Проходную»: микросервисы на Java/Spring за gateway, Python для OCR, события через Kafka + outbox, вход через Keycloak, модель за изолированным `LlmGateway` с локальным провайдером Ollama.

## Быстрый старт (≤ 10 минут)
```bash
git clone <repo> && cd <repo>/subsidy
scripts/gen-env.sh               # infra/.env со случайными секретами; локальная модель: LLM_PROVIDER=ollama
cd infra && docker compose --profile llm-local up -d --build
```
- Специалист: `https://<host>/specialist` → вход через Keycloak (демо `specialist` / `KC_DEMO_SPECIALIST_PASSWORD`)
- Keycloak: `https://<host>/auth` (админка — `KC_BOOTSTRAP_ADMIN_*` из `infra/.env`)
- Health: `https://<host>/actuator/health`, сервисы — внутри сети
- Проверка стенда: `scripts/smoke.sh https://<host>`; сквозная (синтетический пакет → протокол): `EVAL_PASSWORD=<KC_DEMO_SPECIALIST_PASSWORD> scripts/smoke.sh https://<host>` (нужен `uv`)

Требования: Docker 24+ с compose v2, 16 ГБ RAM (локальная модель qwen3:8b + qwen2.5vl:7b ≈ 11 ГБ; первая сборка и скачивание моделей — дольше 10 минут), открытые 80/443. Сертификат — внутренний CA Caddy (`tls internal`), браузер попросит подтвердить. Для сервера с доменом: `PUBLIC_HOST`/`KC_HOSTNAME` в `infra/.env`.

## Документация
| Файл | О чём |
|---|---|
| [AGENTS.md](AGENTS.md) | вход для ИИ-агентов |
| [rules.md](rules.md) | правила разработки и красные линии |
| [docs/PRD.md](docs/PRD.md) | продукт: проблемы, цели, пользователи, MVP |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | FR/NFR с ID и трассировка к критериям положения |
| [docs/DOC-TYPES.md](docs/DOC-TYPES.md) | восемь видов документов: поля, признаки, чек-лист |
| [docs/RULES-CATALOG.md](docs/RULES-CATALOG.md) | правила проверки пакета: формулы, параметры, коды |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | схемы, сервисы, конвейер, модель данных |
| [docs/API.md](docs/API.md) | контракт, стадии и коды |
| [docs/LLM.md](docs/LLM.md) | слой модели и **инструкция по локальному провайдеру** |
| [docs/EVAL.md](docs/EVAL.md) | как честно меряем классификацию и извлечение |
| [docs/DECISIONS.md](docs/DECISIONS.md) | журнал решений и лицензии |
| [docs/SECURITY.md](docs/SECURITY.md) | вход, данные организаций, модель угроз |
| [docs/STACK.md](docs/STACK.md) · [docs/DEPLOY.md](docs/DEPLOY.md) | стек и лицензии, сервер и CI/CD |
| [docs/ROLES.md](docs/ROLES.md) · [docs/PLAN.md](docs/PLAN.md) · [docs/DEMO.md](docs/DEMO.md) | команда, план дня, защита |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) · [docs/TASKS.md](docs/TASKS.md) · [docs/MEMORY.md](docs/MEMORY.md) | процесс, вехи, состояние |
| [docs/AGENT_SKILLS.md](docs/AGENT_SKILLS.md) · [docs/QUESTIONS.md](docs/QUESTIONS.md) | скиллы агентов, открытые вопросы |
| `services/<svc>/docs/`, [frontend/docs/](frontend/docs/) | PRD, ARCHITECTURE, RULES, TASKS, MEMORY модулей; `frontend/docs/DESIGN.md` — дизайн-система |

## Команда
| Участник | Зона ответственности |
|---|---|
| B1 | платформа и безопасность: gateway, package (организации, пакеты, приём файлов и проверки, хранение), Keycloak, CI/CD |
| B2 | классификация и извлечение: docproc (очередь, стадии, тип по содержимому, разделение PDF, поля), ocr (Python), промпты, оценка |
| B3 | правила и модель: rules (комплектность, правила и арифметика, протокол, история, статистика), `libs/llm`, assistant |
| F1 | пакет: загрузка, список файлов с типом и уверенностью, оригинал рядом с результатом, смена типа, дозагрузка |
| F2 | каркас и дизайн-система, очередь пакетов, протокол и выгрузка, организация и история, статистика, помощник |
