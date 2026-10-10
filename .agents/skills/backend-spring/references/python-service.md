# Python-сервис `face`

Зачем Python: детекция/сравнение лиц и антиспуфинг — это ONNX-модели и OpenCV; на Python это проще и быстрее отлаживается, чем OpenCV в JVM. Сервис **не принимает решение о проходе** — только отвечает «насколько похоже» и «живой ли кадр»; решает access.

## Стек
Python 3.12, FastAPI + uvicorn, pydantic v2, `onnxruntime` (CPU), `opencv-python-headless`, numpy, `asyncpg`/`psycopg` (эмбеддинги в своей БД `face`), `cryptography` (AES-GCM для эмбеддингов), `opentelemetry-instrumentation-fastapi`. Менеджер — `uv` (`uv.lock` в git). Качество: `ruff` (lint+format), `mypy --strict`, `pytest`.
Модели (лицензии разрешённые): YuNet (детекция, MIT), SFace (эмбеддинг 128-d, Apache-2.0), MiniFASNet (антиспуфинг, Apache-2.0). **InsightFace/buffalo — нельзя** (веса только для некоммерческих целей). Веса скачивает `scripts/fetch_models.sh` с проверкой sha256, в git не кладём.

## Структура
```
face/
  pyproject.toml  uv.lock  Dockerfile
  src/face/
    main.py                 # create_app(): роутеры, middleware (trace, problem+json), lifespan грузит модели
    api/routes.py           # тонкие обработчики, Depends(current_service)
    api/schemas.py          # pydantic *Rq / *Rs (как в Java)
    core/config.py          # pydantic-settings, env без дефолтов для секретов
    core/security.py        # проверка JWT Keycloak (только service-токены access/people)
    core/errors.py          # DomainError(code) → application/problem+json
    domain/matcher.py       # cosine similarity, порог из конфига
    domain/liveness.py      # MiniFASNet + проверка челленджа по landmarks
    infra/models.py         # загрузка ONNX, warmup
    infra/repository.py     # эмбеддинги в Postgres (bytea, зашифровано)
  tests/unit/  tests/integration/  tests/data/  # синтетика или фото команды с согласия, не в публичном git
```

## Контракт (`/internal/v1/**`, только service-токен)
- `POST /internal/v1/enroll/{workerId}` (multipart jpeg) → `{ quality, faceCount }`; 422 если лиц ≠ 1 или качество низкое.
- `POST /internal/v1/match/{workerId}` (jpeg) → `{ score, threshold, matched, liveness: { score, passed } }`.
- `DELETE /internal/v1/enroll/{workerId}` — удаление биометрии (152-ФЗ).
OpenAPI FastAPI выгружается в `contracts/openapi/face.json` (`uv run python -m face.export_openapi`), CI сверяет.

## Правила
- CPU-инференс синхронный → в `run_in_threadpool`; модели грузятся один раз в lifespan, warmup на старте; readiness = модели загружены.
- Таймаут ответа ≤ 800 мс на кадр 640×480; кадр > 2 МБ — 413.
- Фото не сохраняем, только эмбеддинг (зашифрованный). В логах — ни кадров, ни эмбеддингов.
- Порог совпадения и антиспуфинга — в конфиге, подбирается на тестовом наборе, значение и FAR/FRR — в `docs/DECISIONS.md`.

## Команды
```bash
cd subsidy/services/face
uv sync && scripts/fetch_models.sh
uv run uvicorn face.main:app --reload --port 8086
uv run ruff check . && uv run ruff format --check . && uv run mypy src && uv run pytest
```
