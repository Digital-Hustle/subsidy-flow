# RULES — frontend

Сверху — `../../rules.md` и скилл `frontend-fsd`. ⛔ = PR не принимается.

## Делай
- Работай в своём слайсе (владельцы — `../AGENTS.md`); `shared`/`entities`/`app` — PR с ревью F2.
- Сначала найди существующее (`shared/ui`, `entities`, `features`).
- Данные: `entity/api` → `queries.ts` → компонент; запись — `features/<verb-noun>/api/mutations.ts` + `invalidateQueries`.
- Каждый экран: skeleton / empty / error с «Повторить» / data / offline.
- Тексты статусов, стадий и правил — с бэка (`message`, `detail`, `hint`) или `t("rules.<code>")`.
- Стили — только токены `docs/DESIGN.md`; анимации — `motion/react` и пресеты.
- Перед PR: `subsidy/scripts/ci-local.sh quick`, руками в браузере 1440×900, шаги в PR.

## Избегай
- ⛔ Вычислять на клиенте суммы, НДС, лимиты, сроки, комплектность, статусы правил и пакета (Д11, СН): никаких `reduce` по суммам, сравнений дат для вердикта, `status = "PASSED"`.
- ⛔ Парсить деньги в `number` для расчётов; `shared/lib/money.ts` только форматирует строку.
- ⛔ Тип документа из имени файла или расширения.
- ⛔ Токены в localStorage/sessionStorage; файлы по публичным URL — только blob через `/files/{id}/content` с токеном, `revokeObjectURL` при уходе.
- ⛔ Моки вне `src/mocks` и `import.meta.env.DEV`; примерные пакеты и цифры на пустой БД.
- ⛔ hex/rgb, arbitrary-значения, литералы анимаций, `style={{…}}` с размерами — `fsd-check`.
- ⛔ Дублирующие движки анимаций, `@keyframes` вне `shared/ui`.
- Ручной `fetch`/`axios` в компонентах, серверные данные в zustand, `useEffect` для загрузки.
- Нативные `<select>`, `<input type=date>`, `alert/confirm`.

## Библиотеки
Из `docs/ARCHITECTURE.md §Стек` + реестры `components.json`. Новая зависимость — лицензия MIT/Apache/BSD/ISC, строка в `../../docs/DECISIONS.md`, ревью F2. Запрещены: `tesseract.js` и любые клиентские OCR/парсеры документов.

## Обработка ошибок
- problem+json → `{code, detail, hint, errors[]}`; показываем `detail`/`hint` или `t("errors.<code>")`.
- Отказ приёма файла — состояние строки файла, не toast.
- 401 → refresh, повтор, затем вход; 403 → `ForbiddenPage`; 404 → empty; 409 `NOT_CHECKED_YET` → «Проверка идёт» со стадиями.
- Мутации: `loading`, блок повторного нажатия, `Idempotency-Key` при создании пакета.
- `ErrorBoundary` на зону с `traceId`.

## Ограничения
- Desktop-first (1440×900, 1920×1080); двухпанельный пакет ≥ 1280 px, ниже — вкладки.
- `pdfjs-dist` грузится лениво; большие PDF — постранично.
- Продуктовый код — только в день хакатона (п. 7.3).
