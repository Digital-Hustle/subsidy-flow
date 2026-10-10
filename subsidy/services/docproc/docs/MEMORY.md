# MEMORY — docproc

Память модуля. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- DP-01..11, 13 готовы (#38). DP-12: таблицы из ocr (`pages[].tables`, OCR-06) → `PageText.tables` → в текст для извлечения полей (`textWithTables`), классификатор их не видит. PDF с текстовым слоем тоже идёт через ocr (ради таблиц); ocr недоступен → текст PDFBox без таблиц.
- Открытые вопросы: точный список 8 видов (PLT-00); есть ли склейки в закрытом наборе.

## 2. Карта модуля
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| _появится в Т0_ | по `docs/ARCHITECTURE.md §Структура папок` |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | очередь — `jobs` в PostgreSQL | Д4 | ADR-002 |
| до Т0 | тип по содержимому + сегментация | 25 баллов, СН | ADR-005 |
| до Т0 | модель читает, правила — в rules | Д11 | ADR-006 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| — | — | — |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля
- 2026-10-10 · KIRI4444 · B2-1 (DP-17, PR #4): PROPERTY_DOC — addresses[] с назначением, rightHolder/tenant; GUARANTEE_LETTER — guarantor*/beneficiary* + inn для совместимости; схемы v5. B2-3 (DP-18, PR #10): PageQualityAssessor — UNREADABLE с причиной, PARTIAL_SCAN → NEEDS_REVIEW. B2-2/B2-4 (DP-19): extract.v2 + marks {signature, stamp}, parse_results.marks/page_quality, DocumentParsed.marks/pageQuality, API.md. Ручная смена типа сохраняет source=SPECIALIST/reason/by (старое поведение).
