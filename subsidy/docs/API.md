# Контракт API — «Субсидии» (черновик, финализировать на синке Т0+0:45)

Все запросы идут через gateway: `/<service>/api/v1/...` (`package`, `docproc`, `rules`, `assistant`); gateway переписывает путь в `/api/v1/...` сервиса (как в «Проходной»). Коллекции — `{items: [...], page: {next, total}}`, request — `*Rq`, response — `*Rs`. Внутренние вызовы — `/internal/**`, наружу gateway их не пропускает. Ошибки — `application/problem+json`: `{type, title, status, detail, code, hint, traceId, errors?}` (`detail`/`hint` — по-русски). Так же отвечают отказы фреймворка: нет/просрочен токен → **401** `UNAUTHORIZED` (с `WWW-Authenticate: Bearer`), не хватает роли → **403** `FORBIDDEN`, неверное тело/параметр → **400** `VALIDATION_ERROR` (`errors[]` — поля), слишком большой файл → **413** `FILE_TOO_LARGE` (FR-06, `libs/common/web`). Курсоры, `options`, SSE, `Idempotency-Key` — `../../.agents/skills/backend-spring/references/frontend-contract.md`. Деньги в JSON — **строки** `"12345.67"` (без потерь точности), даты — `YYYY-MM-DD`.

## Аутентификация
Сотрудники входят по коду из письма (ADR-023): токен сессии выпускает `package`, `Authorization: Bearer <JWT>`. Keycloak остаётся издателем только для межсервисных `svc-*` (`client_credentials`); сервисы принимают обоих издателей по `iss`. Роли: `SPECIALIST` (пакеты, загрузка, правки, протокол), `SENIOR_SPECIALIST` (+ решения, программы, переразбор; TOTP), `AUDITOR` (только чтение протоколов, истории, статистики). SSE: `GET /<svc>/api/v1/stream/ticket` → одноразовый `ticket` на 30 с.

### Вход сотрудников (`/package/api/v1/auth`, без токена)
- `GET /auth/mode` → `{mode: demo|prod, demo: [сотрудник], demoCode}` — в `prod` список пуст, `demoCode` = null
- `POST /auth/code` `{email}` → `{sentTo, expiresAt, resendAt, demoCode}` — письмо с 6-значным кодом; `demoCode` приходит только в демо-режиме. Неизвестная почта даёт такой же ответ (список сотрудников не раскрываем). Ошибки: 429 `LOGIN_RESEND_TOO_SOON`, 429 `RATE_LIMITED` (лимит gateway `RATE_LOGINS_PER_MINUTE`=20 по IP). Письмо уходит в фоне после ответа (повторы 3 с, 15 с, 60 с); демо-сотрудникам письма не шлются
- `POST /auth/verify` `{email, code}` → `{accessToken, expiresAt, sessionExpiresAt, user}` + httpOnly-cookie `subsidy_session` (refresh, `SameSite=Strict`, путь `/package/api/v1/auth`). Ошибки: 400 `LOGIN_CODE_INVALID`, 410 `LOGIN_CODE_EXPIRED`, 429 `LOGIN_CODE_LOCKED` (5 попыток)
- `POST /auth/refresh` (cookie) → новая пара, cookie ротируется; 401 `SESSION_EXPIRED`
- `POST /auth/logout` (cookie) → 204, сессия закрыта
- `GET /auth/me` → текущий сотрудник `{id, name, email, role, status, demo, lastSeen}`

### Сотрудники (`/package/api/v1/staff`, только `SENIOR_SPECIALIST`)
- `GET /staff` → `{items: [сотрудник]}`
- `POST /staff` `{name, email, role}` → 201, сотрудник в статусе `INVITED` (войдёт по коду на эту почту)
- `PATCH /staff/{id}` `{role?, active?}` → сотрудник; снятие последнего старшего → 409 `STAFF_LAST_SENIOR`
- `GET /package/internal/staff/jwks` — публичные ключи для проверки токенов сотрудников (внутри сети, наружу gateway не пропускает)

## package (`/package/api/v1`)
- `GET /organizations?inn=` → организация или 404; `POST /organizations` `{inn}` → 201 (ИНН проверяется контрольной суммой, `INN_INVALID`)
- `POST /packages` `{orgInn, programCode}` → 201 `{packageId, status: COLLECTING}` (`Idempotency-Key`)
- `GET /packages?status=&inn=&program=&cursor=` → очередь: `{id, org, program, status, completeness, failed, warnings, needsReview, updatedAt}`
- `POST /packages/{id}/files` multipart `files[]` (файлы или ZIP) → **202** `{files: [{fileId, stage: RECEIVED, duplicateOf?}], rejected: [{name, code, detail, hint}]}` — отказ одного файла не отменяет остальные
- `POST /packages/{id}/files/{fileId}/versions` multipart `file` → 202 (замена / повторная загрузка, FR-65)
- `DELETE /packages/{id}/files/{fileId}` → 204
- `GET /packages/{id}` → `{packageId, status, org, program, protocol: {status, completeness, failed, warnings, needsReview}|null, decision: {decision, comment, decidedBy, decidedAt}|null, updatedAt, files: [{fileId, version, name, mime, pages, stage, reasonCode?, documents: [{documentId, fromPage, toPage, type, typeConfidence, source, stage}]}]}`
- `GET /files/{fileId}/content?version=` → оригинал (роль + аудит); `GET /files/{fileId}/pages/{n}.png` → превью страницы
- `GET /stream?packageId=&ticket=` (SSE, билет из `GET /stream/ticket`) — `file.stage`, `document.parsed`, `package.checked`; heartbeat `: ping` каждые 15 с
- `POST /packages/{id}/decision` `{decision: APPROVE|REJECT|RETURN, comment}` → 200 `{packageId, status, decision, decidedBy, decidedAt}`; только `SENIOR_SPECIALIST`, только при наличии протокола (`PROTOCOL_NOT_FOUND` 422), комментарий обязателен для REJECT/RETURN (400), повтор → `PACKAGE_DECIDED` 409. RETURN возвращает пакет в `COLLECTING` (дозагрузка), APPROVE/REJECT → `DECIDED`; событие `PackageDecided`

## docproc (`/docproc/api/v1`)
- `GET /documents/{id}/result` → `{type, typeConfidence, evidence, fields: [{name, value, raw, page, bbox, confidence, checks}], model, promptVersion, schemaVersion}`
- `PUT /documents/{id}/type` `{type, reason}` → 202 (переизвлечение полей по новому типу, FR-29)
- `POST /files/{fileId}/reprocess` → 202 (`SENIOR_SPECIALIST`)
- `GET /model/status` → `{provider, model, visionModel, reachable, lastLatencyMs}`
- `GET /doc-types` → реестр видов (код, название, поля)

## rules (`/rules/api/v1`)
- `GET /packages/{id}/protocol` → последний протокол: `{protocolId, version, status: COMPLETE|INCOMPLETE|NEEDS_REVIEW, checklistVersion, rulesVersion, completeness: {items: [{item, type, condition, status, documents[]}], missing: [{type, reason}]}, results: [{rule, status, message, evidence: [{documentId, field, page, bbox?}], expected?, actual?}], createdAt}`
- `GET /packages/{id}/protocols` → версии протоколов `[{protocolId, version, status, completeness, failed, warnings, needsReview, createdAt}]`; `GET /packages/{id}/protocols/{version}` → конкретная версия. До первой проверки `GET .../protocol` → 409 `NOT_CHECKED_YET`
- `PUT /packages/{id}/fields` `{documentId, field, value, reason}` → 202 — правка значения специалистом (данные, не вердикт); сервер перепроверяет и создаёт новую версию протокола
- `GET /packages/{id}/protocol/export?format=docx|xlsx|pdf` → файл (B-02)
- `POST /packages/{id}/projection/rebuild` → 202 `{packageId, documents, updated, removed, status: RECHECK_SCHEDULED}` — только `SENIOR_SPECIALIST`: пересборка проекции документов из docproc (`/internal/docproc/...`, токен svc-rules), если событие разбора потеряно. Автоматически — перед расчётом протокола, когда у разобранного файла в проекции нет документов. Ошибки: 404 `PACKAGE_NOT_FOUND`, 503 `DEPENDENCY_UNAVAILABLE`
- `GET /organizations/{inn}/history` → `[{at, by, action, packageId, details}]` (B-03)
- `GET /stats/errors?from=&to=&program=` → `[{rule, count, share}]` + «как посчитано» (B-04)
- `GET /expiring?days=30` → документы, срок которых истекает (B-05)
- `GET /programs`, `GET /programs/{code}` → параметры и чек-лист; `PUT /programs/{code}` — только `SENIOR_SPECIALIST`, новая версия

## assistant (`/assistant/api/v1`)
- `POST /packages/{id}/ask` `{question}` (≤ 1000 симв.) → `{messageId, answer, sources: [{documentId?, field?, rule?}], tools: [имена], validator: {status: OK|NUMBERS_MISMATCH, details: [числа/даты без подтверждения]}, model, createdAt}` (B-06; ответ только из инструментов `get_protocol|get_missing|get_fields|get_anomalies`, ≤ 4 вызова, числа сверяет валидатор). Ошибки: 404 `PACKAGE_NOT_FOUND`, 503 `MODEL_UNAVAILABLE`/`DEPENDENCY_UNAVAILABLE`, 404 `NOT_FOUND` при `ASSISTANT_ENABLED=false`
- `GET /packages/{id}/messages` → `{items: [{messageId, role: USER|ASSISTANT, content, sources, validatorStatus, validatorDetails, createdAt}]}` — диалог текущего пользователя
- `GET /packages/{id}/suggestions` → `{items: [вопрос]}` — подсказки по результатам протокола

## Стадии файла / документа
| stage | Текст специалисту |
|---|---|
| RECEIVED | Файл принят |
| QUEUED | В очереди на разбор (позиция N) |
| EXTRACTING_TEXT | Читаем текст |
| CLASSIFYING | Определяем, какие документы в файле |
| EXTRACTING_FIELDS | Извлекаем данные |
| VALIDATING | Проверяем реквизиты |
| PARSED | Разобран |
| NEEDS_REVIEW | Разобран, есть что проверить (тип или поля под вопросом) |
| UNREADABLE | Не удалось прочитать — см. причину |
| WAITING_MODEL | Модель недоступна, продолжим автоматически |
| FAILED | Не удалось разобрать — можно «разобрать заново» |

## Коды отказов приёма (package, HTTP 4xx)
| code | HTTP | Текст | Подсказка |
|---|---|---|---|
| FILE_EMPTY | 400 | Файл пустой | Выберите файл ещё раз |
| FILE_TOO_LARGE | 413 | Файл больше {max} МБ | Разделите документ или уменьшите разрешение скана |
| FILE_TYPE_UNSUPPORTED | 415 | Такой формат не поддерживается | Подойдут PDF, JPG, PNG, TIFF, HEIC, DOCX, XLSX, ZIP |
| FILE_CORRUPTED | 422 | Файл повреждён и не открывается | Сохраните документ заново |
| PDF_ENCRYPTED | 422 | PDF защищён паролем | Снимите пароль и загрузите снова |
| TOO_MANY_PAGES | 422 | Слишком много страниц ({max}) | Разделите файл |
| ZIP_UNSAFE | 422 | Архив не принят: {reason} | Упакуйте файлы заново без вложенных архивов |
| DUPLICATE_FILE | 409 | Этот файл уже есть в пакете | Загрузите другой документ |
| TOO_MANY_FILES | 422 | В пакете уже {max} файлов | Удалите лишние |
| INN_INVALID | 400 | ИНН не прошёл проверку контрольной суммы | Проверьте цифры ИНН |
| PACKAGE_DECIDED | 409 | По пакету уже принято решение | Создайте новый пакет |
| RATE_LIMITED | 429 | Слишком много загрузок подряд (помощник: «вопросов подряд») | Подождите минуту; заголовок `Retry-After` — сколько секунд. Лимиты gateway: `RATE_UPLOADS_PER_MINUTE`=30, `RATE_QUESTIONS_PER_MINUTE`=20 на пользователя |
| LOGIN_CODE_INVALID | 400 | Код не подошёл | Проверьте код из письма — осталось попыток: N |
| LOGIN_CODE_EXPIRED | 410 | Срок кода истёк | Запросите новый код |
| LOGIN_CODE_LOCKED | 429 | Слишком много попыток | Запросите новый код |
| LOGIN_RESEND_TOO_SOON | 429 | Код уже отправлен | Повторная отправка через N секунд |
| SESSION_EXPIRED | 401 | Сессия истекла | Войдите снова |
| STAFF_LAST_SENIOR | 409 | Это последний старший специалист | Назначьте другого старшего, потом меняйте роль |
| MAIL_UNAVAILABLE | 503 | Не удалось отправить письмо | Повторите позже или обратитесь к администратору стенда |
| PAYLOAD_TOO_LARGE | 413 | Запрос больше {max} | Загрузка ≤ `GATEWAY_UPLOAD_MAX_MB` (512), прочие запросы ≤ `GATEWAY_BODY_MAX_KB` (1024); лимит на файл — `FILE_TOO_LARGE` в package |

## Причины непригодности и замечания (в результате, не HTTP)
| reasonCode | Текст |
|---|---|
| IMAGE_BLURRY / IMAGE_TOO_DARK / IMAGE_CUT_OFF | Скан размыт / слишком тёмный / обрезан — переснимите |
| TEXT_NOT_FOUND | Не удалось найти текст |
| TYPE_UNKNOWN | Не удалось уверенно определить вид документа — выберите вид вручную |
| TYPE_OTHER | Документ не входит в перечень: {summary} |
| CHECKSUM_FAILED | {field} не прошёл проверку контрольной суммы |
| DUPLICATE_DOCUMENT | Такой документ уже есть в пакете (файл {other}) |
| MODEL_UNAVAILABLE | Модель недоступна, продолжим автоматически |
| MODEL_INVALID_OUTPUT | Модель вернула ответ не по схеме — проверьте вручную |

Коды правил и тексты — `RULES-CATALOG.md`.

## Внутренние API (только service accounts, `aud=<сервис>`)
- package: `GET /internal/package/files/{fileId}?version=` → поток (только `svc-docproc`)
- ocr: `POST /v1/extract` multipart `file` → `{mime, pages: [{n, width, height, textLayer, text, words: [{t, bbox, conf, line}], tables: [{rows: [[ячейка]], bbox, source: text_layer|words}], quality: {blur, brightness, glare, cutOff, dpi}, meanConf, rotation, error?}], durationMs}` (таблицы OCR-06: линейки pdfplumber для текстового слоя, иначе строки/колонки по bbox слов; ≥ 3 строк × ≥ 3 ячеек); `POST /v1/render?page=` multipart `file` → PNG. Токен service account `svc-docproc|svc-package` с `aud=ocr`
- docproc: `GET /internal/docproc/packages/{id}/documents` → все результаты пакета (для rules)
- rules: `GET /internal/rules/packages/{id}/facts` → протокол + поля (для assistant, только чтение)