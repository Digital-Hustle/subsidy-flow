# Слой модели и локальный запуск (Д5, Д6, разд. 5.3)

## Изоляция
```
docproc ──► LlmGateway  (libs/llm; интерфейс)
              chat(messages) → ChatResult
              structured(messages, jsonSchema) → JsonNode   // ответ валиден по схеме или исключение
              vision(messages, images, jsonSchema) → JsonNode
              status() → {provider, model, reachable}
   ▲
   ru.subsidy.llm.infra.gigachat.GigaChatAdapter
   ru.subsidy.llm.infra.openai.OpenAiCompatAdapter     // любой OpenAI-совместимый
   ru.subsidy.llm.infra.ollama.OllamaAdapter           // format: <json schema>, keep_alive
```
- Бин выбирается `@ConditionalOnProperty(name = "app.llm.provider")`. Вне `ru.subsidy.llm.infra` никто не знает о провайдерах (ArchUnit `LlmIsolationTest` + `guard.sh`).
- Общее для всех адаптеров: таймаут, retry ×2 с экспоненциальной паузой, circuit breaker (Resilience4j), семафор `LLM_MAX_CONCURRENCY`, метрики `llm_request_seconds{provider,model,outcome}`, запись в `llm_calls` (через SPI, который реализует docproc).
- Ответ проверяется по JSON Schema (networknt json-schema-validator, Apache-2.0). Невалиден → одна перегенерация с текстом ошибки → `MODEL_INVALID_OUTPUT` → `NEEDS_REVIEW`. Модель недоступна / CB открыт → `ModelUnavailableException` → стадия `WAITING_MODEL` (не «готово», СН).

## Настройки (`infra/.env`)
```
LLM_PROVIDER=ollama              # gigachat | openai | ollama
LLM_BASE_URL=http://ollama:11434
LLM_MODEL=qwen3:8b               # текст: классификация и извлечение
LLM_VISION_MODEL=qwen2.5vl:7b    # картинка страницы, если OCR слабый (VISION_FALLBACK_ENABLED)
LLM_API_KEY=                     # только для облака, только в .env
LLM_JSON_MODE=json_schema        # openai: json_schema | json_object | none — что умеет провайдер
LLM_TIMEOUT_SECONDS=60
LLM_MAX_CONCURRENCY=2
GIGACHAT_SCOPE=GIGACHAT_API_PERS
```

## Режимы
| Режим | Когда | Как включить |
|---|---|---|
| **API** | разработка, стенд, CI-smoke, если дадут ключ | `LLM_PROVIDER=openai` (+ `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`) или `gigachat` |
| **Локально (Д6)** | показ без интернета, проверка судьями | `LLM_PROVIDER=ollama` + `docker compose --profile llm-local up -d` |

Пример API-режима (ключ — только в `infra/.env` на сервере, не в git):
```
LLM_PROVIDER=openai
LLM_BASE_URL=https://api.example.com/v1
LLM_MODEL=qwen3.8-flash          # id из GET $LLM_BASE_URL/models
LLM_VISION_MODEL=qwen3-vl-plus
LLM_API_KEY=sk-…
LLM_JSON_MODE=json_object        # если провайдер отклоняет response_format=json_schema
LLM_TIMEOUT_SECONDS=120
```
Запуск без профиля `llm-local`: `docker compose up -d` — контейнер ollama не поднимается. Проверка ключа и модели: `curl -s $LLM_BASE_URL/models -H "Authorization: Bearer $LLM_API_KEY"`; бейдж в рабочем месте — «openai · <модель> · доступна».
Совместимость `response_format` (проверено на шлюзе стенда): `json_schema` — qwen3.8-flash, glm-5.3-flash, qwen3-vl-plus; deepseek-v4-flash — только `json_object`. Ответ в любом режиме проверяется по JSON Schema кодом (ниже), так что `json_object` безопасен.

### Замер API-моделей на стенде (X-04, синтетика `scripts/smoke.sh`, пакет из 6 PDF / 8 документов)
| Модель (текст) | JSON-режим | Классификация, ср. | Извлечение полей, ср. / макс. | Токенов ответа (извлечение) | Пакет целиком | Вид документа | Расхождений правил |
|---|---|---|---|---|---|---|---|
| `deepseek-v4-flash` | json_object | 3,7 с | 10 с / 26 с | ~1100 | **≈ 40 с** | 8/8 | 0 |
| `qwen3.8-flash` | json_object | 4,8 с | 71–86 с / 155 с (таймауты, ретраи) | ~3000 | ≈ 6 мин | 8/8 | 0 |
Выбор для стенда: **`deepseek-v4-flash`** (текст) + `qwen3-vl-plus` (картинки, `VISION_FALLBACK_ENABLED`). Это синтетика, не открытый набор: точность на реальных документах — после DP-15.

OpenAI-совместимые: DeepSeek, Qwen (DashScope), GLM (Z.ai), vLLM. Требования к модели: русский язык, structured output / JSON mode, контекст ≥ 16k; для vision-пути — модель с картинками.

## Выбор локальной модели (FR-42)
| Модель (Ollama) | Лицензия | RAM | Время на документ, CPU | Точность типа | Точность полей | Вывод |
|---|---|---|---|---|---|---|
| `qwen3:8b` | Apache-2.0 | ~6 ГБ | _замер_ | _замер_ | _замер_ | основной кандидат (текст) |
| `qwen3:4b` | Apache-2.0 | ~3 ГБ + контекст | не запустилась на 4 ГБ RAM (OOM при `num_ctx` 8192) | — | — | нужно ≥ 6 ГБ RAM |
| `qwen3:1.7b` | Apache-2.0 | ~2,4 ГБ | ~180 с (2 vCPU, 4 ГБ, без GPU; сегментация + поля) | 8/8 | 22/22 | работает даже на слабой машине; минимальный вариант для офлайн-показа |
| `qwen2.5vl:7b` | Apache-2.0 | ~6 ГБ | _замер_ | _замер_ | _замер_ | vision для сканов |
| `qwen3-vl:8b` | Apache-2.0 (проверить карточку) | ~6 ГБ | _замер_ | _замер_ | _замер_ | альтернатива vision |
| ~~`qwen2.5vl:3b`~~ | Qwen Research (некоммерч.) | — | — | — | — | **нельзя** (п. 5.8.4) |
Замер — `eval/bench_llm.py`: синтетический пакет из `eval/synthetic`, текст страниц из PDF, **те же промпты, схемы и параметры, что в docproc** (`segment.v1`, `extract.v1`, `registry.json`; temperature 0, seed 42, `num_ctx` 8192), без поднятия стенда. Точность типа — по страницам (включая склейку из 3 документов), полей — по размеченным полям синтетики после нормализации (суммы «1 602 532,80 руб.» = 1602532.80, как `MoneyNormalizer`). Строки `qwen3:8b`/VL — замерить на машине с ≥ 8 ГБ RAM той же командой:

```bash
cd eval && uv run --group dev python bench_llm.py --provider ollama --base http://localhost:11434 --model qwen3:8b --seeds 3
```

Перед `ollama pull` любой новой модели — проверить лицензию в карточке и записать строку в `DECISIONS.md`.

## Локальный провайдер — инструкция для судей
1. `scripts/gen-env.sh` (или `cp infra/.env.example infra/.env`) и `LLM_PROVIDER=ollama`.
2. `cd infra && docker compose --profile llm-local up -d` — поднимется `ollama`, init-контейнер `ollama-pull` скачает `LLM_MODEL` и `LLM_VISION_MODEL` (~5 + 6 ГБ).
   Без интернета: `docker load < ollama-image.tar` и том `ollama-models` из архива (`infra/ollama/README.md`).
3. Проверка: `docker compose exec ollama ollama list` — модели в списке; в рабочем месте специалиста бейдж «Ollama · qwen3:8b · доступна».
4. Требования: 16 ГБ RAM, 4+ CPU; с NVIDIA GPU — раскомментировать `deploy.resources` в compose.
5. Демо: выключить интернет → загрузить документ → стадии идут, поля извлечены; `docker compose stop ollama` → `WAITING_MODEL`; `start` → догнал сам.

## Промпты и схемы (FR-45)
- Файлы: `services/docproc/src/main/resources/prompts/{segment,classify,extract}.v<N>.md`; JSON Schema полей — из `doc_types.schema` (версия в результате).
- **Классификация**: системный промпт перечисляет типы реестра с короткими описаниями и признаками из `DOC-TYPES.md`; вход — первые ~4000 символов текста, флаги (`mrzFound`, найденные форматы номеров); выход `{type, confidence, evidence: [строки текста]}`. `evidence` должны встречаться в тексте (проверка кодом), иначе уверенность понижается.
- **Извлечение**: только поля схемы типа; правило «значение — только из текста документа, нет — `null` с причиной»; для каждого поля `raw` (дословно из текста) → код находит `raw` в словах OCR и берёт bbox; не нашёл — уверенность ниже.
- Few-shot — только синтетические примеры из `eval/synthetic/`, никогда из открытого набора (rules.md §1.3).
- qwen3: `/no_think` и `temperature=0` для детерминизма; `seed` фиксируем.

## Сегментация (FR-23)
Для файла > 1 страницы промпт `segment.v<N>` получает по каждой странице первые ~600 символов и признаки «начала документа» (заголовок, номер, дата, КНД) и возвращает `[{fromPage, toPage, type, confidence, evidence}]` по JSON Schema; код проверяет, что сегменты покрывают все страницы без пересечений, иначе — одна перегенерация, затем «весь файл — один документ, NEEDS_REVIEW».

## Помощник (assistant, B-06)
Tool calling через `LlmGateway.chat` с описанием инструментов чтения; финальный ответ проверяет валидатор чисел (каждое число в ответе должно встретиться в результатах инструментов). Модель та же, что для разбора.

## Чего не делаем
Заготовленные ответы и fallback с данными; передачу модели имени файла, порядка и пути в архиве; хранение текстов документов в логах; прямой доступ модели к БД.
