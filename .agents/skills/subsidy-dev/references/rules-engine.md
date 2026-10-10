# Правила и комплектность (15 + часть 20; Д11) — подробно `docs/RULES-CATALOG.md`, `services/rules/docs/`

- Цикл нового правила: строка в `docs/RULES-CATALOG.md` (код, формула, параметры, статус, текст) → тест с числами (граница, копейки, НДС 0/10/20) → класс `engine/rules/<Code>Rule` → прогон на синтетике.
- `Rule.evaluate(PackageFacts, ProgramParams) → RuleResult(status, message, evidence[], expected?, actual?)`. Статусы: PASSED / FAILED / WARNING / NOT_APPLICABLE / DATA_MISSING (нет данных ≠ PASSED) / ERROR (исключение правила — остальные выполняются).
- Деньги — только `Money` (scale 2, HALF_UP в одном месте, сравнение с допуском 0.01); `new BigDecimal(String)`, никогда `double`.
- Параметры программы — `programs.params` (ставка, лимит, сроки, ОКВЭД), чек-лист — `checklists` (обязательный / условный / «один из»); версии попадают в протокол.
- Факты = поле специалиста ?? поле модели; правка поля → `recheck_requests` → debounce 2 с → новый неизменяемый протокол + `PackageChecked`.
- Комплектность — по определённым видам документов, не по файлам.
- Тексты — по-русски с числами: «Запрошено 1 600 000,00, максимум 1 500 000,00 = min(0,50 × 3 000 000,00; 3 000 000,00)».
