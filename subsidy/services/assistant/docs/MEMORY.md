# MEMORY — assistant

Память модуля. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- AS-01..05 готовы: `POST /packages/{id}/ask` — цикл structured-output «tool|answer» (≤ 4 инструмента) через `LlmGateway.structured`, без нативного tool calling (работает на любом провайдере libs/llm).
- Инструменты читают один ответ rules `/internal/rules/packages/{id}/facts` (404 → чужой/нет пакета).
- `NumberValidator`: числа/даты ответа ⊂ числа результатов инструментов и вопроса; целые 0…10 не проверяются.
- Диалог — один на (пакет, пользователь), в `conversations/messages`; журнал вызовов модели — `llm_calls`.

## 2. Карта модуля
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `chat/` | AskService (цикл), Prompts, ConversationStore, SuggestionService |
| `tools/` | get_protocol, get_missing, get_fields, get_anomalies |
| `validate/` | NumberValidator |
| `rules/` | RulesClient (service account svc-assistant) |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | инструменты + валидатор чисел | как assistant «Проходной» | ADR-014 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | производные числа (сумма, посчитанная моделью) помечаются NUMBERS_MISMATCH | так и задумано: ответ показывается со статусом |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля
