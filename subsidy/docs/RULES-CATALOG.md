# Каталог правил проверки (кейс 3, п. 4.3)

Правила — данные программы (`programs.params.rules`, ADR-024): администратор правит их на странице «Программы» без релиза, сервер проверяет схему (`VALIDATION_ERROR`). Ниже — набор, который миграция `20261010090000-case-programs.sql` кладёт во все три программы. Версия движка — `RULES_VERSION` в протоколе.

| Код | Что проверяет | Операция | Важность | Параметры |
|---|---|---|---|---|
| `MIN_FIELDS` | Минимум полей вычитан | `REQUIRED_FIELDS` | WARNING | `{"fields": {"APPLICATION": ["orgName", "inn", "requestedAmount", "date"], "PD_CONSENT": ["fullName", "date"], "CERTIFICATE": ["number", "validUntil"], "PRESENTATION": ["projectName"], "COST_JUSTIFICATION": ["totalCost", "ownFunds"], "BANK_STATEMENT": ["orgName", "balance", "date"], "PROPERTY_DOC": ["cadastralNumber"], "GUARANTEE_LETTER": ["guarantorName", "amount", "date"], "GIMS_DECLARATION": ["number", "validUntil"]}}` |
| `INN_FORMAT` | ИНН — 10 или 12 цифр | `PATTERN` | FAILED | `{"types": ["*"], "field": "inn", "normalize": "digits", "pattern": "\\d{10}\|\\d{12}", "hint": "10 цифр у организации, 12 — у ИП"}` |
| `INN_SAME` | ИНН одинаков во всех документах | `SAME_VALUE` | FAILED | `{"types": ["*"], "field": "inn", "includeApplicant": true}` |
| `DATE_NOT_FUTURE` | Дата документа не в будущем | `DATE_NOT_FUTURE` | FAILED | `{"types": ["*"], "field": "date"}` |
| `NOT_EXPIRED` | Срок действия не истёк | `NOT_EXPIRED` | FAILED | `{"types": ["CERTIFICATE", "GIMS_DECLARATION"], "field": "validUntil", "warnDays": 30}` |
| `OWN_FUNDS_SHARE` | Собственные средства не менее 50 % расходов | `RATIO_MIN` | FAILED | `{"numerator": ["COST_JUSTIFICATION.ownFunds", "COST_JUSTIFICATION.totalCost - COST_JUSTIFICATION.requestedAmount", "APPLICATION.ownFunds", "COST_JUSTIFICATION.totalCost - APPLICATION.requestedAmount", "APPLICATION.totalCost - APPLICATION.requestedAmount"], "denominator": ["COST_JUSTIFICATION.totalCost", "APPLICATION.totalCost"], "min": "0.50"}` |
| `REQUEST_MATCH` | Сумма в заявлении совпадает с обоснованием затрат | `EQUAL_SUM` | FAILED | `{"pairs": [{"left": "APPLICATION.requestedAmount", "right": "COST_JUSTIFICATION.requestedAmount"}, {"left": "APPLICATION.requestedAmount", "right": "COST_JUSTIFICATION.totalCost - COST_JUSTIFICATION.ownFunds"}, {"left": "APPLICATION.totalCost", "right": "COST_JUSTIFICATION.totalCost"}]}` |
| `CADASTRAL_FORMAT` | Кадастровый номер в принятом формате | `PATTERN` | FAILED | `{"types": ["PROPERTY_DOC"], "field": "cadastralNumber", "normalize": "compact", "pattern": "\\d{2}:\\d{2}:\\d{6,7}:\\d+", "hint": "АА:ВВ:ССССССС:КК, например 61:44:0050706:12"}` |
| `PRESENTATION_NOT_EMPTY` | Презентация не пустая | `NOT_EMPTY` | FAILED | `{"types": ["PRESENTATION"], "fields": ["projectName", "description"]}` |

## Операции
| op | Смысл | Параметры |
|---|---|---|
| `REQUIRED_FIELDS` | поля вычитаны | `fields: {ВИД: [поля]}` |
| `PATTERN` | значение по шаблону | `types`, `field`, `pattern`, `normalize` (digits/compact), `hint` |
| `SAME_VALUE` | значение одинаково во всех документах | `types`, `field`, `includeApplicant` |
| `DATE_NOT_FUTURE` | дата не позже сегодня | `types`, `field` |
| `NOT_EXPIRED` | срок действия не истёк (предупреждение за `warnDays`) | `types`, `field`, `warnDays` |
| `RATIO_MIN` | доля ≥ `min` | `numerator[]`, `denominator[]` — выражения, берётся первое вычислимое; `min` |
| `EQUAL_SUM` | суммы совпадают (допуск 0.01) | `pairs[{left,right}]` — первая вычислимая пара |
| `NOT_EMPTY` | поля не пустые | `types`, `fields` |
| `RANGE` | число в диапазоне | `operand`, `min?`, `max?` |

Операнд — `ВИД.поле` или разность `ВИД.a - ВИД.b`; `*` в `types` — все виды пакета. Нет данных для проверки — правило в статусе `DATA_MISSING` с причиной, а не «пройдено».

## Правила вне программы
| Код | Что | Где |
|---|---|---|
| `CHK_REQUIRED`, `CHK_ONE_OF`, `CHK_CONDITIONAL`, `CHK_DUPLICATE`, `CHK_UNKNOWN` | комплектность по чек-листу программы | `CompletenessEngine` |
| `ANOMALY_OUTLIER` | статья обоснования затрат выше mean + 3σ по программе (ADR-019) | `OutlierRule` |

