# Что бэкенд отдаёт фронту, чтобы интерфейс был живым

Фронт строится на кастомных анимированных контролах (`frontend-fsd`, ADR-031): бесконечные списки, комбобоксы с поиском, живые ленты, счётчики, пошаговая проверка на киоске. Ниже — что для этого нужно от каждого сервиса. Правила API.md и rules.md важнее. Новое поле в контракте — сначала API.md, потом код.

## 1. Списки
- **Таблицы админки** (рабочие, смены): `?page=0&size=50&sort=fullName,asc` → `{ items, total, page, size }`. Сортировка только по whitelist полей, `size ≤ 200`.
- **Журнал и ленты** (попытки прохода, история рабочего): keyset-курсор `?cursor=<opaque>&limit=50` → `{ items, nextCursor }`. Курсор = base64url от `(created_at, id)`. Индекс `(created_at desc, id desc)`. Под `useInfiniteQuery` и виртуальный список: offset на append-only журнале даёт дубли при вставках.
- Фильтры — query-параметры, повторяемые для мультиселекта: `?zoneId=a&zoneId=b&decision=DENY&from=…&to=…` (ISO-8601, UTC).

## 2. Опции для select / combobox
- `GET /<entity>/options?q=&limit=20` → `[{ id, label, hint?, avatarUrl? }]`. Для рабочих, объектов, зон, бригад, КПП.
- Поиск: `ILIKE` по нормализованному полю + индекс `pg_trgm` (часть PostgreSQL, лицензия PostgreSQL). `q` короче 2 символов → первые `limit` по алфавиту.
- p95 ≤ 150 мс: фронт шлёт запрос с debounce 200 мс и анимирует появление опций.

## 3. Дашборд и аналитика
- Каждый ответ с числами: `{ value, previous?, asOf, source: "REAL" | "SIMULATOR" }`. `previous` — прошлое **реальное** значение: с него стартует number-ticker. `source` обязателен, интерфейс помечает симулятор (rules.md §1.5).
- Ряды для графиков: `[{ bucket: ISO, value }]` с заполненными нулями (без дыр), `bucket` в часовом поясе объекта, зона явно в ответе.
- Считаем только из событий в БД, без кэша «на глаз». Кэш допустим поверх БД с TTL ≤ 10 с.

## 4. SSE (shift — хаб)
- Каждое событие: `id: <eventId>`, `event: attempt.created`, `data: {…минимальный payload + version}`.
- Поддержка `Last-Event-ID`: при переподключении досылаем пропущенное из журнала, чтобы живая лента не теряла строки.
- Heartbeat-комментарий `: ping` каждые 15 с (прокси и мобильная сеть не рвут соединение).
- Payload уже готов к показу: `workerName`, `zoneName`, `decision`, `code`. Фронт не делает N запросов на каждое событие.

## 5. Ошибки для «живых» форм
`application/problem+json`: `{ type, title, status, code, message, hint?, errors?: [{ field, code, message }] }`.
- `errors[]` — поле подсвечивается и «вздрагивает» точечно, а не общим тостом.
- `hint` — что делать человеку («Обновите QR в телефоне»). Тексты по-русски, коды стабильные, из API.md.

## 6. Киоск: пошаговая проверка (предложение B2 ↔ F1, утвердить на синке)
В ответ `POST /kiosk/attempts/{id}/frames` добавить `checks: [{ type: "QR" | "FACE" | "LIVENESS" | "PERMIT" | "SHIFT" | "ANTIPASSBACK", passed, ms }]`. Киоск проигрывает multi-step-loader **по этим фактам с сервера** уже после ответа (≤ 600 мс), а не по таймеру. Решение по-прежнему одно — `decision` (rules.md §1.1).

## 7. Время
- Заголовок `X-Server-Time: <epoch ms>` во всех ответах gateway (фильтр). Телефон синхронизирует кольцо 30-секундного окна QR, киоск — таймер автосброса.
- Даты в API — ISO-8601 UTC. Длительности — секунды (число).

## 8. Мутации
- Ответ содержит обновлённый ресурс: фронт заменяет строку без перезапроса, `layout`-анимация отрабатывает сразу.
- `Idempotency-Key` (UUID в заголовке) на необратимых действиях: ручной проход охранника, блокировка, выдача инвайта. Повтор с тем же ключом → тот же ответ. Slide-to-confirm и двойной тап не создают дублей. Хранение — таблица `idempotency_keys(key, request_hash, response, created_at)` с TTL 24 ч.

## 9. Картинки (аватары, `photoThumb`)
- `GET /people/api/v1/workers/{id}/avatar?size=96|256` → `image/webp`, `Cache-Control: private, max-age=300`, `ETag`. Только авторизованным ролям, расшифровка AES-GCM на лету (ADR-009), без логирования.
- В списках отдаём `avatarUrl`, не base64. base64 `photoThumb` допустим только в ответе киоска и в офлайн-снимке (ADR-029).

## 10. Заголовки для фронта (gateway / Caddy)
- CSP: `font-src 'self' https://cdn-app.sberdevices.ru; style-src 'self' 'unsafe-inline' https://cdn-app.sberdevices.ru` (SB Sans с CDN). `img-src 'self' data: blob:`. `connect-src` — свой домен и Keycloak.
- CORS — только origin фронта. `Access-Control-Expose-Headers: X-Server-Time, ETag`.

## Тесты
Под каждый пункт, который реализован: integration-тест в `integrations/` (курсор без дублей при вставке между страницами, `Last-Event-ID` досылает пропуски, повтор `Idempotency-Key` не создаёт вторую запись, `errors[]` при 400).
