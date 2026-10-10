# TASKS — ocr

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| OCR-01 | FR-21 | Каркас FastAPI, JWT, health, Dockerfile с tesseract-ocr-rus, CI python | B2 | done #48 |
| OCR-02 | FR-21 | PDF: текстовый слой с bbox, рендер pypdfium2 | B2 | done #48 |
| OCR-03 | FR-21 | Предобработка: EXIF, deskew, CLAHE, OSD | B2 | done #48 |
| OCR-04 | FR-21 | Tesseract `rus+eng`: слова + bbox + conf | B2 | done #48 |
| OCR-05 | FR-26 | Метрики качества | B2 | done #48 |
| OCR-06 | FR-32 | Таблицы: pdfplumber + строки/колонки по bbox для сканов | B2 | done #66 |
| OCR-07 | FR-63 | `/v1/render` превью страниц | B2 | done #48 |
