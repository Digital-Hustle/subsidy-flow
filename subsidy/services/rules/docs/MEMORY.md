# MEMORY — rules

Память модуля. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- B1-2: `PackageApplicationChanged` обновляет снимок и ревизию заявки, запрашивает перепроверку; ревизия включена в факты и `PackageChecked` (ADR-026).
- RUL-01..16 готовы (#43): проекция из событий → debounce-перепроверка (`recheck_requests`, SKIP LOCKED) → `ProtocolService.check` → неизменяемый протокол + `PackageChecked`.
- FR-30: правила п. 4.3 — данные программы (`programs.params.rules`), движок `engine/declarative` (ProgramRules + 9 операций), запуск — `ProgramRulesRule`; хардкод — только `OutlierRule`. Программы INNOVATION/BEACH/MODERNIZATION (миграция `20261010090000-case-programs.sql`), DEMO-2026 удалена. RULES_VERSION 2026.10-3.
- Протокол создаётся только когда все файлы в терминальной стадии; новая версия — только при изменении фактов.
- Liquibase contexts: прод `prod`, демо-стенд `prod,demo` (env `LIQUIBASE_CONTEXTS`).
- RUL-18: `projection/ProjectionRebuild` + `DocprocClient` (svc-rules → docproc `/internal`): `POST /packages/{id}/projection/rebuild` (SENIOR) и авто-пересборка в `RecheckWorker`, если у PARSED/NEEDS_REVIEW-файла нет документов в проекции. Сверка — `ProjectionService.reconcile` (upsert по resultVersion, снятие отсутствующих).
- Нет: ANOMALY_OUTLIER (RUL-17).
- RUL-17: SUM_WORDS (#56), ANOMALY_OUTLIER — фон программы из `stats/EstimateBaselineRepository` (SQL-агрегат), порт `engine/ProgramBaseline`. RULES_VERSION 2026.10-2.
- Нет: пересборка проекции (RUL-18).

## 2. Карта модуля
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `money/` | Money — scale 2, HALF_UP, допуск 0.01 |
| `facts/` | FactsAssembler (проекция + правки специалиста), FieldOverrideService |
| `program/` | программы и чек-листы как данные |
| `engine/`, `engine/rules/` | RuleEngine (RULES_VERSION) и правила |
| `completeness/` | полнота, «чего не хватает», текст запроса |
| `projection/`, `messaging/` | события package/docproc → проекция |
| `recheck/` | debounce-очередь перепроверок + воркер |
| `protocol/` | снимки, запросы, экспорт DOCX/XLSX/PDF (шрифт Liberation Sans) |
| `history/`, `stats/` | история организации, частые ошибки, истекающие документы |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | правила — код, параметры — данные | Д11, смена программы без кода | ADR-006 |
| до Т0 | деньги — BigDecimal, в JSON строками | копейки | ADR-007 |
| до Т0 | протокол — неизменяемый снимок | объяснимость | ADR-008 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| — | — | — |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля

- 2026-10-10 · Codex · B1-2/FR-47: перепроверка после изменения формы заявки и защита от старой ревизии, PR #1.
