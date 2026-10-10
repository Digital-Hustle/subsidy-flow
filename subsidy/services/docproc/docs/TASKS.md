# TASKS — docproc

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| DP-01 | NFR-15 | `new-service.sh docproc 8082 B2`, Liquibase: jobs, stage_events, stage_outputs, outbox | B2 | done |
| DP-02 | FR-20 | `JobClaimer` + lease + `WorkerPool`, `ConcurrentClaimTest` | B2 | done |
| DP-03 | FR-20 | `StageMachine` + `RestartMidStageTest` | B2 | done |
| DP-04 | FR-20 | Консьюмер `FileReceived`/`FileReplaced` → jobs | B2 | done |
| DP-05 | FR-21 | EXTRACTING_TEXT: ocr + POI для DOCX/XLSX, UNREADABLE | B2 | done |
| DP-06 | FR-22/24 | `doc_types` + 8 схем, `Classifier`, уверенность, `TYPE_UNKNOWN`/`OTHER`; `RenamedFilesSameTypeTest` | B2 | done |
| DP-07 | FR-30 | `FieldExtractor` по схеме, перегенерация | B2 | done |
| DP-08 | FR-31 | `MoneyNormalizer`, `DateNormalizer`, `RequisitesChecks` — тест на каждую | B2 | done |
| DP-09 | FR-23 | `Segmenter`: склейки → документы, `SegmenterCoverageTest` | B2 | done |
| DP-10 | FR-27 | WAITING_MODEL, backoff, FAILED | B2 | done |
| DP-11 | FR-29 | `PUT /documents/{id}/type` → переизвлечение, история результатов | B2 | done |
| DP-12 | FR-32 | Таблицы позиций (pdfplumber tables / строки по bbox) | B2 | done #67 |
| DP-13 | FR-34/53 | `parse_results` с версиями, `llm_calls`, `/model/status` | B2 | done |
| DP-14 | FR-26 | Vision-путь при слабом OCR + замер | B2 | partial #81: OCR_WEAK по meanConf → картинка модели; замер на открытом наборе — на стенде |
| DP-15 | B-08 | Калибровка уверенности типа на открытом наборе, отчёт | B2 | blocked: открытого набора нет ни в репо, ни на сервере; инструмент готов — метрика `docproc_type_confidence` (#80) + `eval/run.py` |
| DP-16 | NFR-07 | Метрики стадий и уверенности | B2 | done #80 |
