# Конвейер: вид документа (25) и поля — подробно `docs/ARCHITECTURE.md §Конвейер`, `docs/DOC-TYPES.md`, `docs/EVAL.md`

1. `FileReceived` → `jobs`. Стадии: RECEIVED → QUEUED → EXTRACTING_TEXT → CLASSIFYING → EXTRACTING_FIELDS → VALIDATING → PARSED | NEEDS_REVIEW | UNREADABLE (+ WAITING_MODEL, FAILED).
2. Текст: ocr `/v1/extract` (PDF/изображения, таблицы) или POI (DOCX/XLSX).
3. Файл > 1 страницы → `segment.v<N>` → сегменты `[fromPage, toPage, type, confidence, evidence]`; код проверяет покрытие страниц; сегмент = документ. 1 страница → `classify.v<N>`. Ниже `CLASSIFY_MIN_CONFIDENCE` → `TYPE_UNKNOWN`; вне реестра → `OTHER`.
4. Поля: `extract.v<N>` + JSON Schema вида (таблицы позиций для смет и счетов).
5. Нормализация: деньги → `BigDecimal` (пробелы, запятые, «руб.»), даты → ISO, ИНН/ОГРН/КПП/БИК/р/с с контрольными суммами → `checks[]`, уверенность.
6. `DocumentParsed` → rules. Смена типа специалистом → переизвлечение, старый результат сохраняется.

## Качество без подгонки
- Изменил промпт/схему/порог → `eval/run.py --set synthetic` (+ open, + adversarial: переименования, перемешивание, склейки, инъекции) → строка в отчёте в PR.
- Few-shot — только `eval/synthetic/`. Никаких ИНН/наименований/имён файлов открытого набора в коде и промптах (`guard.sh`).
