# Локальная модель (Ollama) — запуск, в том числе без интернета

Кейс «Субсидии» работает с любой из трёх реализаций `LlmGateway` (`docs/LLM.md`). Этот каталог — про
локальный провайдер: что поднять, чем проверить и как перенести модели на машину без интернета.

## 1. Запуск с интернетом

```bash
cd subsidy/infra
cp .env.example .env            # или scripts/gen-env.sh
sed -i 's/^LLM_PROVIDER=.*/LLM_PROVIDER=ollama/' .env
docker compose --profile llm-local up -d
```

Поднимется контейнер `ollama`, а init-контейнер `ollama-pull` скачает `LLM_MODEL` и `LLM_VISION_MODEL`
из `.env` (по умолчанию `qwen3:8b` ≈ 5 ГБ и `qwen2.5vl:7b` ≈ 6 ГБ). Первый запуск — 10–20 минут на скачивание.

Проверка:

```bash
docker compose exec ollama ollama list                  # модели на месте
curl -s localhost:11434/api/tags | jq '.models[].name'  # то же по HTTP
```

В рабочем месте специалиста бейдж показывает «ollama · qwen3:8b · доступна» (`GET /docproc/api/v1/model/status`).

## 2. Перенос на машину без интернета

На машине с интернетом (один раз):

```bash
subsidy/infra/ollama/pack.sh /path/to/bundle     # ~12 ГБ: образ + веса моделей
```

Скрипт сохраняет:

- `ollama-image.tar` — образ `ollama/ollama` нужной версии;
- `ollama-models.tar` — содержимое тома с весами (`/root/.ollama`);
- `MODELS.txt` — какие модели внутри и из какого `.env` они взяты.

На машине без интернета:

```bash
subsidy/infra/ollama/load.sh /path/to/bundle
cd subsidy/infra && docker compose --profile llm-local up -d ollama
docker compose exec ollama ollama list
```

`load.sh` загружает образ и распаковывает веса в том `subsidy_ollama`, поэтому `ollama-pull` ничего не качает.
Остальные образы стенда (postgres, kafka, keycloak, caddy, jaeger) переносятся так же: `docker save` → `docker load`.

## 3. Требования и настройки

| Что | Значение |
|---|---|
| RAM | 16 ГБ (8 ГБ хватает только для `qwen3:4b`) |
| CPU | 4+ ядра; документ на CPU — около 40–60 с |
| GPU (необязательно) | NVIDIA + `nvidia-container-toolkit`, раскомментировать `deploy.resources` у `ollama` в `docker-compose.yml` |
| Диск | ~15 ГБ под веса и образ |

Переменные `.env`: `LLM_PROVIDER=ollama`, `LLM_BASE_URL=http://ollama:11434`, `LLM_MODEL`, `LLM_VISION_MODEL`,
`LLM_JSON_MODE=json_schema` (Ollama умеет строгую схему), `LLM_TIMEOUT_SECONDS=120` для слабого CPU,
`LLM_MAX_CONCURRENCY=1..2` по памяти.

Мало памяти — возьмите модели поменьше и пропишите их в `.env` до `up`:

```bash
LLM_MODEL=qwen3:4b
LLM_VISION_MODEL=qwen2.5vl:7b
```

## 4. Проверка «как судья»

1. `docker compose stop ollama` → загрузите документ: стадия становится `WAITING_MODEL`, протокол не «проверен».
2. `docker compose start ollama` → разбор догоняет сам, без повторной загрузки файла.
3. Отключите машине сеть и повторите разбор: с локальным провайдером всё проходит целиком.

## 5. Лицензии весов

Используем только разрешающие лицензии (`rules.md §1.12`): `qwen3:8b`, `qwen3:4b`, `qwen2.5vl:7b` — Apache-2.0.
Перед `ollama pull` новой модели проверьте карточку модели и добавьте строку в `docs/DECISIONS.md`.
Модели под некоммерческими лицензиями (например, `qwen2.5vl:3b` — Qwen Research) брать нельзя.
