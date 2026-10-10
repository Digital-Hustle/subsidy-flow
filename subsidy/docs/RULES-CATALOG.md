# Каталог правил проверки (кейс 3, п. 4.3)

Правила — данные программы (`programs.params.rules`, ADR-024): администратор правит их на странице «Программы» без релиза, сервер проверяет схему (`VALIDATION_ERROR`). Ниже — набор, который миграции `20261010090000-case-programs.sql` и `20261010112645-application-document-checks.sql` кладут во все три программы. Версия движка — `RULES_VERSION` в протоколе.

| Код | Что проверяет | Операция | Важность | Параметры |
|---|---|---|---|---|
| `MIN_FIELDS` | Минимум полей вычитан | `REQUIRED_FIELDS` | WARNING | `{"fields": {"APPLICATION": ["orgName", "inn", "requestedAmount", "date"], "PD_CONSENT": ["fullName", "date"], "CERTIFICATE": ["number", "validUntil"], "PRESENTATION": ["projectName"], "COST_JUSTIFICATION": ["totalCost", "ownFunds"], "BANK_STATEMENT": ["orgName", "balance", "date"], "PROPERTY_DOC": ["cadastralNumber"], "GUARANTEE_LETTER": ["guarantorName", "amount", "date"], "GIMS_DECLARATION": ["number", "validUntil"]}}` |
| `INN_FORMAT` | ИНН — 10 или 12 цифр | `PATTERN` | FAILED | `{"types": ["*"], "field": "inn", "normalize": "digits", "pattern": "\\d{10}\|\\d{12}", "hint": "10 цифр у организации, 12 — у ИП"}` |
| `INN_SAME` | ИНН одинаков во всех документах | `SAME_VALUE` | FAILED | `{"types": ["APPLICATION", "PRESENTATION", "COST_JUSTIFICATION", "BANK_STATEMENT"], "field": "inn", "includeApplicant": true}` |
| `DATE_NOT_FUTURE` | Дата документа не в будущем | `DATE_NOT_FUTURE` | FAILED | `{"types": ["*"], "fields": ["date", "issueDate"]}` |
| `NOT_EXPIRED` | Срок действия не истёк | `NOT_EXPIRED` | FAILED | `{"types": ["CERTIFICATE", "GIMS_DECLARATION"], "field": "validUntil", "warnDays": 30}` |
| `OWN_FUNDS_SHARE` | Собственные средства не менее 50 % расходов | `RATIO_MIN` | FAILED | `{"numerator": ["COST_JUSTIFICATION.ownFunds", "COST_JUSTIFICATION.totalCost - COST_JUSTIFICATION.requestedAmount"], "denominator": ["COST_JUSTIFICATION.totalCost"], "min": "0.50"}` |
| `REQUEST_MATCH` | Сумма в заявлении совпадает с обоснованием затрат | `EQUAL_SUM` | FAILED | `{"pairs": [{"left": "APPLICANT.requestedAmount", "right": "COST_JUSTIFICATION.requestedAmount"}, {"left": "APPLICANT.requestedAmount", "right": "COST_JUSTIFICATION.totalCost - COST_JUSTIFICATION.ownFunds"}, {"left": "APPLICATION.requestedAmount", "right": "COST_JUSTIFICATION.requestedAmount"}, {"left": "APPLICATION.requestedAmount", "right": "COST_JUSTIFICATION.totalCost - COST_JUSTIFICATION.ownFunds"}]}` |
| `CADASTRAL_FORMAT` | Кадастровый номер в принятом формате | `PATTERN` | FAILED | `{"types": ["PROPERTY_DOC"], "field": "cadastralNumber", "normalize": "compact", "pattern": "\\d{2}:\\d{2}:\\d{6,7}:\\d+", "hint": "АА:ВВ:ССССССС:КК, например 61:44:0050706:12"}` |
| `PRESENTATION_NOT_EMPTY` | Презентация не пустая | `NOT_EMPTY` | FAILED | `{"types": ["PRESENTATION"], "fields": ["projectName", "description"]}` |

## Операции
| op | Смысл | Параметры |
|---|---|---|
| `REQUIRED_FIELDS` | поля вычитаны | `fields: {ВИД: [поля]}` |
| `PATTERN` | значение по шаблону | `types`, `field`, `pattern`, `normalize` (digits/compact), `hint` |
| `SAME_VALUE` | значение одинаково во всех документах | `types`, `field`, `includeApplicant` |
| `DATE_NOT_FUTURE` | Дата документа не в будущем | `DATE_NOT_FUTURE` | FAILED | `{"types": ["*"], "fields": ["date", "issueDate"]}` |
| `NOT_EXPIRED` | срок действия не истёк (предупреждение за `warnDays`) | `types`, `field`, `warnDays` |
| `RATIO_MIN` | доля ≥ `min` | `numerator[]`, `denominator[]` — выражения, берётся первое вычислимое; `min` |
| `EQUAL_SUM` | суммы совпадают (допуск 0.01) | `pairs[{left,right}]` — первая вычислимая пара |
| `NOT_EMPTY` | поля не пустые | `types`, `fields` |
| `APPLICATION_MATCH` | поле заявки совпадает с относящимися к заявителю полями | `applicationPath`, `fields: {ВИД: [поля]}`, `normalize`, `whenPath/whenValue` |
| `SIGNATURE_PRESENCE` | наличие ЭП PDF и отдельно обязательность | `types`, `required` |
| `RANGE` | число в диапазоне | `operand`, `min?`, `max?` |

Операнд — `ВИД.поле` или разность `ВИД.a - ВИД.b`; `*` в `types` — все виды пакета. Нет данных для проверки — правило в статусе `DATA_MISSING` с причиной, а не «пройдено».

## Правила вне программы
| Код | Что | Где |
|---|---|---|
| `CHK_REQUIRED`, `CHK_ONE_OF`, `CHK_CONDITIONAL`, `CHK_DUPLICATE`, `CHK_UNKNOWN` | комплектность по чек-листу программы | `CompletenessEngine` |
| `ANOMALY_OUTLIER` | статья обоснования затрат выше mean + 3σ по программе (ADR-019) | `OutlierRule` |

# Сверки Backend 3 (B3-1…B3-6)

- `APPLICATION_MATCH`: сравнение значения сохранённой заявки (`applicationPath`) с полями документов (`fields` по видам),
  нормализация `digits/text/compact`; `whenPath/whenValue` ограничивают адрес назначением и правообладателя видом права.
  Отсутствующие сведения дают `DATA_MISSING`, обе стороны сохраняются в `expected/actual`.
- Гарантийное письмо: сравниваются `beneficiaryInn/beneficiaryName`; `guarantorInn` и совместимое `inn` относятся к гаранту.
  ИНН гаранта не участвует в `INN_SAME`. Неизвестный получатель не считается подтверждённой связью с заявителем.
- `DATE_NOT_FUTURE` поддерживает `fields`, включая `date` и `issueDate`.
- `OWN_FUNDS_SHARE`: только `COST_JUSTIFICATION.ownFunds` либо `totalCost-requestedAmount` из того же обоснования.
  Данные заявления не подставляются в расчёт доли. `REQUEST_MATCH` никогда не заменяется равенством общих расходов.
  Денежный операнд `APPLICANT.requestedAmount` ссылается на сохранённую форму заявки.
- Условия чек-листа читаются из `application.conditions`; настройки программы описывают условие, но не его выполнение проектом.
- `SIGNATURE_PRESENCE`: информационное правило по умолчанию (`required=false`); обязательность и серьёзность нарушения
  задаются администратором для конкретных видов. `UNKNOWN` даёт неопределённость, не отсутствие/успех;
  наличие ЭП PDF не подтверждает действительность и не доказывает подпись конкретной страницы.

