---
name: frontend-fsd
description: Use for ANY frontend change in a React + TypeScript + Vite project organised by Feature-Sliced Design (layers app/pages/widgets/features/entities/shared), including subsidy/frontend. Covers where a file goes, how to name slices, entity API (dto → mapper → model → TanStack Query key factory), feature mutations with optimistic updates, pages with lazy loading and route config with role guards, i18n namespaces, shadcn/Tailwind UI, axios instance with refresh, zustand stores, and the fsd-check import linter; also the Sber visual identity (colors, SB Sans, logo/photo rules), Motion-only animations (scroll, swipe, layout, kiosk verdict), fully custom animated controls from shadcn registries (Animate UI, Magic UI, Aceternity, React Bits, Eldora, Animata, Motion Primitives, Cult UI, Smooth UI, 21st.dev) via the shadcn/magicui MCP servers and browser screenshots + vision for references (21st.dev, demos). Trigger on "сделай экран", "добавь страницу", "новая фича", "добавь сущность", "подключи эндпоинт на фронте", "куда положить компонент", "почини импорты", "ревью фронта", "сделай красиво", "добавь анимацию", "кастомный селект/дропдаун", "цвета Сбера", "дизайн экрана".
---

# frontend-fsd

Эталон — `MelevaTe/hackathon-spring-2026-frontend` (выигранный хакатон команды). Здесь его правила, очищенные от найденных там нарушений. Для «Субсидий» поверх этого действуют `subsidy/rules.md` и `references/adaptation.md`.

## 0. Перед изменением
1. `cat package.json` — убедись, что стек тот же (React 19, Vite, TS strict, Tailwind 4 + shadcn, motion, axios, TanStack Query, zustand, react-hook-form, i18next). Если другой — не тащи эти правила вслепую.
2. Определи **слой** и **слайс** по таблице ниже. Сомневаешься между слоями — бери нижний (entities < features < widgets), поднимешь позже.
3. Шаблоны файлов — `references/templates.md`. Копируй их, не изобретай форму.

## 1. Слои (сверху вниз; импортировать можно только из слоёв НИЖЕ)
| Слой | Что лежит | Имена слайсов | Сегменты |
|---|---|---|---|
| `app/` | провайдеры, роутер, глобальные стили, `main.tsx` | — | `providers/<Name>/`, `styles/` |
| `pages/` | экран целиком = один маршрут; только компоновка виджетов | `PascalCase` + `Page` (`WorkerListPage`) | `ui/X.tsx`, `ui/X.async.tsx`, `index.ts` |
| `widgets/` | самостоятельные блоки страницы (сайдбар, таблица с фильтрами) | `PascalCase` | `ui/`, `model/` |
| `features/` | одно пользовательское действие с побочным эффектом | `kebab-case` глагол-сущность (`create-worker`, `revoke-device`) | `api/mutations.ts`, `model/`, `lib/`, `ui/` |
| `entities/` | бизнес-сущность: тип, загрузка, отображение без действий | `PascalCase` ед. ч. (`Worker`, `PassAttempt`) | `api/dto.ts`, `api/<x>Api.ts`, `api/queries.ts`, `lib/mappers.ts`, `model/types`, `model/consts`, `ui/`, `index.ts` |
| `shared/` | без бизнес-смысла: ui-кит, axios, конфиг, утилиты, роуты-константы | сегменты, не слайсы | `api/`, `config/`, `const/`, `hooks/`, `lib/`, `types/`, `ui/<Comp>/<Comp>.tsx` |

## 2. Правила импортов и анимаций (проверяет `scripts/fsd-check.mjs`)
- **layer-direction**: только вниз. `shared` не импортирует ничего, `entities` — только `shared`, …, `app` — всё. Нужна тема/роутер в виджете — значит это `shared` (`shared/lib/theme`, `shared/const/router`), а не `app`.
- **cross-slice**: слайс не импортирует соседа по слою. Два виджета нужны вместе → собирает страница. Две сущности связаны → передавай данные пропсами или вынеси общий тип в `shared/types` (`@x`-нотацию FSD не используем).
- **public-api**: чужой слайс — только через `@/layer/Slice` (его `index.ts`). Глубокий импорт `@/entities/Worker/ui/WorkerCard` запрещён. Исключение: `shared/*` импортируется по сегментам (`@/shared/ui/Button/Button`).
- **relative-inside-slice**: внутри своего слайса — только относительные пути (`../model/types`), не `@/features/same-slice/...`.
- **banned-import** / **css-animation** (ADR-036): основной движок — `motion/react`. WebGL (`three`, `@react-three/*`, `postprocessing`, `ogl`), GSAP и Lenis разрешены, где дают заметно лучший визуал (не смешивать два движка в одном компоненте). Запрещены дубли: `framer-motion`, `react-spring`, `animejs`, `popmotion`. CSS `@keyframes`/`animate-*` — только в `shared/ui` (код реестров), в остальных слоях ошибка.
- **hardcoded-color / arbitrary-value / spacing-scale / motion-literal / inline-style**: стиль только из токенов (ADR-033): без hex/rgb, без `p-[13px]`, отступы по сетке `spacingSteps`, без чисел в `transition`, без размеров в `style`. В `shared/ui/` (код shadcn/реестров) — предупреждения, F2 вычищает при адаптации.
- **no-public-api**: у каждого слайса `entities|features|widgets|pages` есть `index.ts`, экспортирующий только нужное снаружи.

```bash
node .agents/skills/frontend-fsd/scripts/fsd-check.mjs <path/to/src>            # exit 1 при нарушениях
node .agents/skills/frontend-fsd/scripts/fsd-check.mjs <path/to/src> --warn-only
```

## 3. Данные
- **Сущность владеет чтением**: `dto.ts` (контракт бэка как есть) → `mappers.ts` (`mapWorkerDto`: нормализация, даты в `Date`, enum-строки в const) → `model/types` (то, с чем работает UI). Компоненты никогда не видят DTO.
- `<x>Api.ts` — объект `workerApi = { list, get, ... }` поверх `$api` из `shared/api`. Никаких `fetch`/`axios` в компонентах.
- `queries.ts` — фабрика ключей `workerKeys = { all, lists(), list(params), detail(id) }` + хуки `useWorkersQuery`. Маппинг в `queryFn` или `select`. Опрос — `refetchInterval`, не `setInterval`.
- **Фича владеет записью**: `features/<verb-noun>/api/mutations.ts` → `useCreateWorkerMutation`. После успеха `invalidateQueries({ queryKey: workerKeys.lists() })`. Оптимистично — только для обратимых действий (избранное, порядок), с `onMutate`/откатом в `onError`.
- Ошибки: `toast.error(t("errors.x"))` — текст из i18n или из `ProblemDetail.detail` ответа, не захардкоженная строка.
- Глобальное клиентское состояние — zustand-стор в слайсе-владельце (`entities/Session/model/sessionStore.ts`) с `devtools` и именами экшенов `"session/setUser"`. Серверные данные в zustand не копируем — это кэш TanStack Query.

## 4. UI
- Компоненты — `memo` + `interface XProps` + именованный экспорт; страницы — `default export` (для `lazy`).
- Каждый список/экран: skeleton (loading) / empty (текст из i18n) / error (кнопка «Повторить» → `refetch`) / data.
- shadcn-компоненты лежат в `shared/ui/<Comp>/<Comp>.tsx`, стили через `cn()` из `shared/lib/utils` и `cva`-варианты. Цвета, размеры, отступы, радиусы, тени, длительности — только токены из `shared/config/tokens.ts` (новый токен → туда + `npm run gen:tokens`), не hex/`p-[13px]`/`duration: 0.3` в коде (ADR-033).
- Формы: `react-hook-form` + схема (`zod` через `@hookform/resolvers`), маппер «форма → DTO» в `features/x/lib/`.
- Тексты — только `t("key")`. Namespace = страница/фича (`public/locales/ru/workers.json`), общие — `common`. Ключи добавляй сразу в `ru` (и `en`, если есть).

## 5. Маршруты
- `shared/const/router.ts`: `AppRoutes` (const-объект) + `getRouteWorkers()`/`getRouteWorker(id)` — ссылки строим только через них.
- `app/providers/router/config/routeConfig.tsx`: `Record<AppRoutes, AppRouteProps>` с `path`, `element`, `authOnly`, `roles?`. Новый экран = новая запись + `getRouteX` + lazy-страница. Охрана — `RequireAuth`/`RequireRole` в `AppRouter`, не внутри страницы.

## 6. Проверка перед PR
```bash
npm run lint && npm run typecheck && npm run build
node .agents/skills/frontend-fsd/scripts/fsd-check.mjs src
```
Prettier (printWidth 90, двойные кавычки, один атрибут на строку, сортировка tailwind-классов) и ESLint `import/order` правят `npm run lint -- --fix` и husky/lint-staged.

## 7. Частые ошибки эталона (не повторять)
Найдено `fsd-check` в эталоне (8 на 250 файлов): `widgets/ThemeSwitcher` и `widgets/MindMapCanvas` импортируют из `app` (тема/провайдер → перенести в `shared`); `widgets/AppShell` импортирует соседний виджет `Navbar` (композицию — в странице или в `app`-layout); 4 alias-импорта внутри собственного слайса. Плюс: токены в `localStorage`, нефиксированные тексты в `toast`, смешанные имена слайсов фич (PascalCase и kebab-case) — у нас только kebab-case.

## 8. Дизайн, бренд и анимации (ADR-031)
Цель — интерфейс уровня продукта Сбера, а не «дефолтный shadcn». Перед версткой экрана прочитай:
1. **`subsidy/frontend/docs/DESIGN.md`** — дизайн-система: токены (сгенерированные таблицы), типографика, отступы, спецификация компонентов, зоны. Единый источник значений — `assets/tokens.ts` → `scripts/gen-tokens.mjs` → `assets/theme.css` (ADR-033); `references/sber-brand.md` — откуда значения, логотип и фото.
2. `references/motion.md` — анимации только на Motion (`motion/react`): токены `assets/motion.ts` → `shared/config/motion.ts`, паттерны прокрутки, свайпов, layout, сценарии киоска и телефона.
3. `references/custom-controls.md` — каждый контрол кастомный: поведение от Radix/cmdk, вид и движение — наши. Чек-лист «после установки».
4. `references/component-catalog.md` — что брать из Magic UI, Aceternity, React Bits, Animate UI, Motion Primitives, Eldora, Animata, Cult UI, Smooth UI, Uiverse, 21st.dev и где искать референсы (Designeer). Подбор по экранам.

Жёсткие правила:
- **Сначала найди, потом пиши.** Ищи компонент через MCP `shadcn` (реестры из `assets/components.json`) или `magicui`; референсы без MCP (21st.dev, демо-сайты) — по `references/component-catalog.md` §«Визуальные референсы», смотри `dependencies`, ставь через `npx shadcn@latest add @ns/name` и доводи по чек-листу. С нуля пишем, только если в каталоге нет подходящего.
- **Картинки и логотипы — только по запросу к человеку.** Спроси, какие нужны: реальные (пришлёт сам) или сгенерированные. Ничего не скачивай со стоков. Фото рабочих и лиц в git и `public/` не кладём никогда.
- В пути прохода (киоск) анимация не задерживает вердикт, а цвет и иконка берутся из `code` ответа сервера.
- Перед PR с UI прогони скиллы `review-animations` и `webapp-testing` (скриншоты трёх зон, mobile 390×844 для `/worker`).

Вспомогательные скиллы (лежат рядом в `.agents/skills/`): `shadcn` (CLI и реестры), `frontend-design` и `design-taste-frontend` (визуальный уровень), `emil-design-eng`, `animate`, `improve-animations`, `review-animations`, `find-animation-opportunities` (качество движения), `webapp-testing` (Playwright). При конфликте этот скилл и `subsidy/rules.md` важнее.
