# TASKS — rules

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| RUL-01 | NFR-15 | `new-service.sh rules 8083 B3`, Liquibase: programs, checklists, package_view, protocols, rule_results | B3 | done #43 |
| RUL-02 | FR-41/NFR-18 | `Money` + тесты (копейки, HALF_UP, допуск) | B3 | done #43 |
| RUL-03 | FR-60 | Роли SPECIALIST/SENIOR/AUDITOR, `RoleMatrixTest` | B3 | done #43 |
| RUL-04 | FR-35 | `programs`/`checklists` как данные + демо-программа (контекст demo) | B3 | done #43 |
| RUL-05 | FR-36/39 | Проекция из событий + `CompletenessEngine` (обязательные, условные, «один из») | B3 | done #43 |
| RUL-06 | FR-42 | Правила реквизитов: ИНН, ОГРН, КПП, БИК+счёт | B3 | done #43 |
| RUL-07 | FR-41 | Правила арифметики `SUM_*` — тест с числами на каждое | B3 | done #43 |
| RUL-08 | FR-45/46 | Протокол-снимок с версиями, `PackageChecked`, API протокола | B3 | done #43 |
| RUL-09 | FR-43 | Правила дат и сроков `DATE_*`, `ORG_*` | B3 | done #43 |
| RUL-10 | FR-44/B-01 | Сверка между документами `CROSS_*` | B3 | done #43 |
| RUL-11 | FR-47 | Правки полей → debounce-перепроверка → новая версия | B3 | done #43 |
| RUL-12 | FR-37/B-07 | «Чего не хватает и почему» + текст запроса организации | B3 | done #43 |
| RUL-13 | B-02 | Экспорт протокола DOCX/XLSX/PDF | B3 | done #43 |
| RUL-14 | B-03 | `org_history` append-only + API истории | B3 | done #43 |
| RUL-15 | B-04/B-05 | Статистика частых ошибок, истекающие документы | B3 | done #43 |
| RUL-16 | B-06 | Правила аномалий `ANOMALY_*` (WARNING) | B3 | done #43 |
| RUL-17 | FR-41/B-06 | `ANOMALY_OUTLIER` (цена vs медиана по программе), `SUM_WORDS` (сумма прописью) | B3 | done #56, #63 |
| RUL-18 | NFR-15 | Пересборка проекции из `docproc /internal/.../documents` при пропуске событий | B3 | done #76 |
| RUL-19 | FR-30 | Программы и правила п. 4.3 по тексту кейса — данные программы, правка без кода | B3 | done |
