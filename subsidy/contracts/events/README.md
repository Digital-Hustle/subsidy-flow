# События Kafka — контракты

Конверт любого события — [`envelope.schema.json`](envelope.schema.json) (`libs/common` `EventEnvelope`), ключ сообщения — `aggregateId` (порядок в партиции). Здесь — схемы `payload` по топикам: `<topic>/<Type>.schema.json`. Доставка — outbox (at-least-once), потребители идемпотентны по `eventId` (`IdempotentConsumer`).

| Топик | Тип | Производитель | Потребители |
|---|---|---|---|
| `package.package.v1` | `PackageCreated`, `PackageDecided` | package | rules (проекция, история организации) |
| `package.file.v1` | `FileReceived`, `FileReplaced`, `FileRemoved` | package | docproc (очередь разбора), rules (проекция) |
| `docproc.document.v1` | `StageChanged`, `DocumentsSegmented`, `DocumentParsed`, `DocumentUnreadable`, `DocumentFailed` | docproc | package (стадии, SSE), rules (проекция → перепроверка) |
| `rules.package.v1` | `PackageChecked` | rules | package (статус пакета, SSE `package.checked`) |

Правила изменения: новое необязательное поле — совместимо; удаление/переименование поля или смена смысла — новый топик `*.v2`. Схемы генерирует `python3 gen.py` (поля сверены с `outbox.append(...)` в коде); правка события = правка `gen.py` + кода в одном PR. Соответствие проверяется автоматически: после каждого интеграционного теста package/docproc/rules все записи `outbox` валидируются по этим схемам (`libs/common` testFixtures `OutboxContract`).
