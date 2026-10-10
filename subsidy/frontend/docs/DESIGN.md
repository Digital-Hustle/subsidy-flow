# DESIGN — дизайн-система «Субсидий»

**Главное правило: стиль задаётся в одном месте.** Все цвета, шрифты, размеры текста, отступы, радиусы, тени, слои и параметры анимаций живут в `src/shared/config/tokens.ts` (до хакатона — шаблон `.agents/skills/frontend-fsd/assets/tokens.ts`). Из него `npm run gen:tokens` генерирует `src/app/styles/theme.css` (Tailwind 4 + shadcn) и таблицы в разделе 2 этого файла. Motion читает те же значения через `src/shared/config/motion.ts`. Литералы в коде ловит `fsd-check`, устаревшую тему — `guard.sh`/`verify.sh` (ADR-033).

Ниже в тексте — только **имена токенов**, значения смотри в таблицах раздела 2 (они сгенерированы и не расходятся с кодом).

## 0. Как поменять или добавить токен
1. Правь `tokens.ts` (владелец F2; F1 — через PR с ревью F2). Новому токену — комментарий «для чего».
2. `npm run gen:tokens` (до хакатона: `node --experimental-strip-types .agents/skills/frontend-fsd/scripts/gen-tokens.mjs .agents/skills/frontend-fsd/assets/tokens.ts --css .agents/skills/frontend-fsd/assets/theme.css --md subsidy/frontend/docs/DESIGN.md`).
3. Генератор проверит контраст пар `x / x-foreground` (WCAG ≥ 4.5:1; исключения — `contrastLargeTextOnly` с причиной).
4. Закоммить `tokens.ts` + `theme.css` + `DESIGN.md` одним коммитом. Конфликт в `theme.css` не правим руками — перегенерируем.

Package-скрипт (добавить при скаффолдинге): `"gen:tokens": "node --experimental-strip-types --no-warnings ../../.agents/skills/frontend-fsd/scripts/gen-tokens.mjs src/shared/config/tokens.ts --css src/app/styles/theme.css --md docs/DESIGN.md"`.

## 1. Характер
От Сбера обязательны только **фирменный зелёный и SB Sans** (ADR-036). Публичная часть гайдлайна Nova — стартовая точка, не закон: полный брендбук закрыт для внешних, поэтому остальное (радиусы, вспомогательные цвета, глубина, движение) — наша современная система.
- Светлые спокойные поверхности (`background`, `card`), много воздуха, крупная типографика SB Sans, мягкие радиусы.
- **Один акцент — зелёный.** Действие = `primary` (кнопка Nova). «Зелёный СБЕР» = `brand` — плоскости, иконки, крупный текст, крупные статусы «готово». Мелкий зелёный текст — только `text-accent-foreground`.
- **Мастер-градиент** (`bg-brand-gradient`, `text-brand-gradient`, `bg-brand-gradient-conic`) — подпись бренда, дозированно: фон входа, кольцо таймера QR, бордер-бим активной карточки, пустые состояния. Не на кнопках, таблицах и тексте длиннее двух слов.
- Нейтральные — только наши серые через семантику (`muted`, `secondary`, `border`). Дефолтная палитра Tailwind (`slate`, `red-500` …) выключена в теме — такие классы просто не сгенерируются.
- Логотип — **заглушка** (`shared/ui/Logo`: знак-галочка в градиенте + слово «Субсидии»), пока не будет официального. Фото — только от человека (см. `frontend-fsd/references/sber-brand.md`).
- Токены общие с «Проходной» (ADR-013): `decision-*`, `text-5xl/6xl`, `z-kiosk`, `motion.kiosk.*` в этом кейсе не используются.

## 2. Токены (сгенерировано)
<!-- tokens:begin -->
<!-- Сгенерировано gen-tokens.mjs из tokens.ts. Правь tokens.ts и запускай `npm run gen:tokens`. -->

#### Семантические цвета

| Токен | Класс | Светлая | Тёмная | Контраст с -foreground (свет / тём.) |
|---|---|---|---|---|
| `background` | `bg-background` / `text-background` | `#E2F5E9` | `#121417` |  |
| `foreground` | `bg-foreground` / `text-foreground` | `#172419` | `#EDF1EF` |  |
| `card` | `bg-card` / `text-card` | `#FFFFFF` | `#1A1D21` | 16.12 / 14.84 |
| `popover` | `bg-popover` / `text-popover` | `#FFFFFF` | `#212429` | 16.12 / 13.66 |
| `surface` | `bg-surface` / `text-surface` | `#EDF8F1` | `#212429` | 14.81 / 13.66 |
| `surface-hover` | `bg-surface-hover` / `text-surface-hover` | `#D6F2E1` | `#292D33` |  |
| `muted` | `bg-muted` / `text-muted` | `#F5FBF7` | `#212429` | 5.69 / 5.87 |
| `border` | `bg-border` / `text-border` | `#D6F2E1` | `#292D33` |  |
| `border-strong` | `bg-border-strong` / `text-border-strong` | `#A9DDC0` | `#33383F` |  |
| `input` | `bg-input` / `text-input` | `#A9DDC0` | `#33383F` |  |
| `ring` | `bg-ring` / `text-ring` | `#21A038` | `#3FC86A` |  |
| `primary` | `bg-primary` / `text-primary` | `#0E8429` | `#3FC86A` | 4.83 / 8.74 |
| `primary-hover` | `bg-primary-hover` / `text-primary-hover` | `#0B7323` | `#7EE29A` |  |
| `brand` | `bg-brand` / `text-brand` | `#21A038` | `#3FC86A` |  |
| `accent` | `bg-accent` / `text-accent` | `#EEF9F0` | `#0B2614` | 6.18 / 10.17 |
| `success` | `bg-success` / `text-success` | `#21A038` | `#3FC86A` |  |
| `success-soft` | `bg-success-soft` / `text-success-soft` | `#DDF3E2` | `#103A1F` | 5.72 / 8.03 |
| `success-border` | `bg-success-border` / `text-success-border` | `#7EE29A` | `#0B6B26` |  |
| `warning` | `bg-warning` / `text-warning` | `#9A5200` | `#F5B86B` |  |
| `warning-soft` | `bg-warning-soft` / `text-warning-soft` | `#FFF1DC` | `#3A2A12` | 6.11 / 7.86 |
| `warning-border` | `bg-warning-border` / `text-warning-border` | `#F5B86B` | `#8A4B00` |  |
| `danger` | `bg-danger` / `text-danger` | `#C9302C` | `#FF8A80` | 5.33 / 8.30 |
| `danger-soft` | `bg-danger-soft` / `text-danger-soft` | `#FDECEA` | `#3D1A1A` | 5.75 / 9.09 |
| `danger-border` | `bg-danger-border` / `text-danger-border` | `#FFB4AB` | `#B42318` |  |
| `info` | `bg-info` / `text-info` | `#175CD3` | `#8AB4F8` |  |
| `info-soft` | `bg-info-soft` / `text-info-soft` | `#E8F0FE` | `#17263D` | 5.22 / 7.22 |
| `info-border` | `bg-info-border` / `text-info-border` | `#8AB4F8` | `#175CD3` |  |
| `inverse` | `bg-inverse` / `text-inverse` | `#172419` | `#EDF1EF` | 14.56 / 16.19 |
| `overlay` | `bg-overlay` / `text-overlay` | `rgb(23 36 25 / 0.36)` | `rgb(0 0 0 / 0.6)` |  |
| `decision-allow` | `bg-decision-allow` / `text-decision-allow` | `#0F7A2E` | `#0F7A2E` | 5.46 / 5.46 |
| `decision-deny` | `bg-decision-deny` / `text-decision-deny` | `#B42318` | `#B42318` | 6.57 / 6.57 |
| `decision-manual` | `bg-decision-manual` / `text-decision-manual` | `#9A5200` | `#9A5200` | 5.86 / 5.86 |
| `decision-error` | `bg-decision-error` / `text-decision-error` | `#292D33` | `#292D33` | 13.84 / 13.84 |
| `chart-1` | `bg-chart-1` / `text-chart-1` | `#21A038` | `#3FC86A` |  |
| `chart-2` | `bg-chart-2` / `text-chart-2` | `#14A3A8` | `#39C0C3` |  |
| `chart-3` | `bg-chart-3` / `text-chart-3` | `#7EE29A` | `#7EE29A` |  |
| `chart-4` | `bg-chart-4` / `text-chart-4` | `#6B7C8F` | `#8FA0B3` |  |
| `chart-5` | `bg-chart-5` / `text-chart-5` | `#9A5200` | `#F5B86B` |  |

Пары `x-foreground` — цвет текста/иконок на фоне `x`. Исключения по контрасту (только крупный текст):


#### Шкалы (только для токенов, не в className)

| Шкала | Значения |
|---|---|
| white | `#FFFFFF` |
| black | `#08130B` |
| ink | 400 `#8C9590`, 600 `#5C665E`, 700 `#3A453D`, 900 `#172419` |
| mint | 25 `#F5FBF7`, 50 `#EDF8F1`, 100 `#E2F5E9`, 150 `#D6F2E1`, 200 `#C4EDD6`, 300 `#A9DDC0`, 400 `#86C9A4`, sber1 `#BBF2D5`, sber2 `#D4F7E0`, sber3 `#EEF7FB` |
| mist | 25 `#F7F9F8`, 50 `#F1F4F3`, 100 `#EBEFEE`, 150 `#E3E9E7`, 200 `#D9E0DE`, 300 `#C5CFCC`, 400 `#A6B1AD` |
| graphite | 100 `#EDF1EF`, 200 `#D5DBD8`, 400 `#98A19D`, 600 `#454B53`, 700 `#33383F`, 750 `#292D33`, 800 `#212429`, 850 `#1A1D21`, 900 `#121417`, 950 `#0E1013` |
| green | 50 `#EEF9F0`, 100 `#DDF3E2`, 300 `#7EE29A`, 400 `#3FC86A`, 500 `#21A038`, 600 `#0F7A2E`, 650 `#0E8429`, 700 `#0B7323`, 800 `#0B6B26`, 900 `#103A1F`, 950 `#0B2614` |
| teal | 300 `#7CD9DA`, 400 `#39C0C3`, 500 `#14A3A8` |
| amber | 100 `#FFF1DC`, 300 `#F5B86B`, 700 `#9A5200`, 800 `#8A4B00`, 900 `#3A2A12` |
| red | 100 `#FDECEA`, 300 `#FFB4AB`, 400 `#FF8A80`, 700 `#C9302C`, 800 `#B42318`, 900 `#3D1A1A` |
| blue | 100 `#E8F0FE`, 400 `#8AB4F8`, 800 `#175CD3`, 900 `#17263D` |
| slate | 400 `#8FA0B3`, 500 `#6B7C8F` |
| sber | green `#21A038`, sky `#00ADEE`, blue `#0087CD`, arctic `#42E3B4`, spring `#A0E720`, sun `#FAED00`, mint `#54DC87`, lagoon `#39C0C3`, lime `#64EE61`, cyan `#32B8D3`, deep `#0B5E2A`, ocean `#0A4F6B` |

#### Типографика (шкала Nova; дефолтные размеры Tailwind выключены)

| Класс | Размер / интерлиньяж | Для чего |
|---|---|---|
| `text-xs` | 12 / 16 px | подписи, бейджи, оси графиков |
| `text-sm` | 14 / 20 px | основной текст админки, ячейки, кнопки sm |
| `text-base` | 16 / 24 px | текст телефона, инпуты (на мобиле ≥16, иначе iOS зумит) |
| `text-lg` | 18 / 26 px | лид, заголовок карточки |
| `text-xl` | 22 / 28 px | заголовок секции |
| `text-2xl` | 26 / 32 px | заголовок страницы на мобиле, крупные цифры |
| `text-3xl` | 32 / 40 px | заголовок страницы, KPI |
| `text-4xl` | 42 / 50 px | герой на мобиле, вердикт-подсказка |
| `text-5xl` | 56 / 62 px | герой лендинга, вердикт киоска |
| `text-6xl` | 72 / 76 px | вердикт киоска на больших экранах |

Шрифты: `font-sans` — SB Sans Text (весь UI), `font-display` — SB Sans Display (h1–h3, KPI, вердикт). Начертания: `font-regular` 400, `font-medium` 500, `font-semibold` 600, `font-bold` 700.

#### Отступы, контролы, радиусы, тени, слои

- Сетка 4 px. Для `p-* m-* gap-* space-* inset-*` разрешены шаги: `0` (0 px), `0.5` (2 px), `1` (4 px), `1.5` (6 px), `2` (8 px), `2.5` (10 px), `3` (12 px), `3.5` (14 px), `4` (16 px), `5` (20 px), `6` (24 px), `7` (28 px), `8` (32 px), `10` (40 px), `12` (48 px), `14` (56 px), `16` (64 px), `20` (80 px), `24` (96 px).
- Высота контролов: `h-control-xs` 32, `h-control-sm` 36, `h-control-md` 44, `h-control-lg` 52, `h-control-xl` 64 px.
- Радиусы: `rounded-2xs` 6, `rounded-xs` 10, `rounded-sm` 14, `rounded-md` 18, `rounded-lg` 26, `rounded-xl` 34, `rounded-2xl` 44, `rounded-3xl` 56 px, `rounded-full`.
- Тени: `shadow-xs`, `shadow-card`, `shadow-float`, `shadow-pop`, `shadow-glow`, `shadow-scrim` (свои значения в тёмной теме).
- Safe-area телефона: `pt-safe`, `pb-safe`.
- Слои: `z-base` 0, `z-raised` 1, `z-sticky` 10, `z-nav` 20, `z-overlay` 50, `z-modal` 60, `z-dropdown` 65, `z-toast` 70, `z-kiosk` 80.

#### Движение (motion/react через `@/shared/config/motion`; CSS-переходы — классами)

| Токен | Значение | CSS-класс |
|---|---|---|
| duration.instant | 0.12 с | `duration-instant` |
| duration.fast | 0.2 с | `duration-fast` |
| duration.base | 0.3 с | `duration-base` |
| duration.slow | 0.45 с | `duration-slow` |
| duration.draw | 0.42 с | `duration-draw` |
| duration.zoom | 0.63 с | `duration-zoom` |
| duration.loop | 2.4 с | `duration-loop` |
| ease.out | `cubic-bezier(0.22, 1, 0.36, 1)` | `ease-out` |
| ease.in | `cubic-bezier(0.4, 0, 1, 1)` | `ease-in` |
| ease.inOut | `cubic-bezier(0.65, 0, 0.35, 1)` | `ease-in-out` |
| ease.linear | `cubic-bezier(0, 0, 1, 1)` | `ease-linear` |
| spring.press | stiffness 520, damping 22, mass 0.6 | — |
| spring.snappy | bounce 0.28, visualDuration 0.32 | — |
| spring.soft | bounce 0.22, visualDuration 0.5 | — |
| spring.pop | bounce 0.45, visualDuration 0.45 | — |
| spring.sheet | bounce 0.16, visualDuration 0.42 | — |
| spring.page | bounce 0.18, visualDuration 0.45 | — |
| spring.bar | bounce 0.3, visualDuration 0.7 | — |
| spring.hover | bounce 0, visualDuration 0.35 | — |
| spring.glide | bounce 0.12, visualDuration 0.4 | — |
| spring.counter | stiffness 110, damping 13, mass 0.9 | — |
| swipe | offset 96, velocity 500 | — |
| toast | durationMs 3600, errorDurationMs 6000 | — |
| kiosk | resultHoldMs 4000, challengeTimeoutMs 10000, motion-sampleMs 200, motion-holdMs 6000, motion-pixelDelta 26, motion-ratio 0.02, motion-width 64, motion-height 48 | — |
<!-- tokens:end -->

## 3. Типографика — как применять
- `font-sans` (SB Sans Text) — весь UI; `font-display` (SB Sans Display) — h1–h3, KPI-цифры, итоговые статусы. Цифры табличные (включено в `body`).
- На экране не больше трёх размеров и двух начертаний. Основной текст: рабочее место `text-sm`, формы `text-base`.
- Иерархия: страница `text-2xl font-semibold` · секция `text-xl font-semibold` · карточка `text-lg font-medium` · подпись `text-xs text-muted-foreground`.
- Пакет: статус пакета `text-2xl font-display font-semibold`, суммы `text-sm font-medium tabular-nums`, «ожидалось/фактически» — `text-xs text-muted-foreground` + значение `text-sm`.
- Тексты на «вы», коротко: что случилось + что делать («Снимок размыт. Держите телефон неподвижно и снимите ещё раз»).

## 4. Отступы, сетка, раскладка
- Сетка 4 px; разрешённые шаги — в разделе 2 (`spacing-scale` в `fsd-check`). Внутри контрола `gap-2`, внутри карточки `p-4`/`p-6` и `gap-3`/`gap-4`, между карточками `gap-4`/`gap-6`, между секциями `gap-8`/`gap-12`.
- Поля страницы: `px-6 lg:px-8`.
- Сайдбар `w-64`, страница пакета — две панели на всю ширину, таблицы во всю ширину; ниже 1280 px — вкладки «Документы / Оригинал / Протокол».
- Тач-цели: `h-control-sm`/`h-control-md` (desktop).

## 5. Компоненты (спецификация)
Поведение и доступность — от примитива (Radix/shadcn, cmdk), вид и движение — наши (`frontend-fsd/references/custom-controls.md`, каталог — `component-catalog.md`). Всё лежит в `shared/ui/<Comp>/`, варианты — через `cva`.

| Компонент | Вид | Варианты / размеры | Состояния и движение |
|---|---|---|---|
| Button | `rounded-md font-semibold px-5`, текст `text-base` (md) | `primary` (bg-primary, hover:bg-primary-hover), `secondary`, `ghost`, `outline`, `destructive`, `link`; `h-control-sm/md/lg`, крупное действие телефона `lg` | `press` (scale hover/tap), `loading` → спиннер на motion + блок повторного клика, `focus-visible:ring-2 ring-ring`, disabled `opacity-50` |
| IconButton | квадрат `size-control-*`, `rounded-md` | как Button | то же; обязательный `aria-label` |
| Input / Textarea | `h-control-md rounded-md border-input bg-card px-4 text-sm` | с иконкой, с маской (телефон, код) | focus `ring-2 ring-ring`, ошибка `border-destructive` + текст `text-xs text-destructive` |
| Select / Combobox / DatePicker | кастомные (Radix Popover + cmdk / react-day-picker), никаких нативных | single/multi, с поиском (`options?q=` бэка) | `scaleIn` поповера, `spring.snappy` индикатора |
| Checkbox / Switch / Radio / Segmented | `rounded-xs` / pill / круг / `rounded-md` трек | sm/md | `spring.press` для ползунка и галочки |
| Card | `bg-card rounded-lg shadow-card p-4 md:p-6` | `interactive` (hover `shadow-sm`→`card`), `selected` (бордер-бим градиентом) | `inView` при прокрутке, `layout` при перестройке |
| Badge / Chip | `rounded-sm px-2 text-xs font-medium` | `neutral`, `success`(accent), `warning`, `destructive`, `info`; статус документа — по `stageView` | без анимации, кроме появления |
| Dialog / Sheet | `bg-popover rounded-xl shadow-pop`, overlay `bg-overlay` | desktop — центр, телефон — bottom sheet `rounded-t-xl` | `scaleIn` / `spring.sheet`, свайп вниз `swipe.*` |
| Tabs | подчёркивание или сегмент | — | индикатор `layoutId` + `spring.snappy` |
| Table | строка `h-control-md`, `text-sm`, шапка sticky `bg-muted text-xs`, числа справа | сортировка, фильтры в URL, пагинация курсором | skeleton строк, `layout` при сортировке |
| Toast | `rounded-lg shadow-pop` | success / error / info | длительность `motion.toast.*`, `fadeUp` |
| Skeleton | `bg-muted rounded-md` | строки/карточки | мерцание на motion (`opacity`), не `animate-pulse` |
| Empty / Error / Offline | иллюстрация или иконка `size-12 text-muted-foreground`, заголовок `text-lg`, текст, кнопка | empty, error («Повторить»), offline-баннер `bg-warning` | `fadeUp` |
| KPI-плитка | `Card` + `font-display text-3xl` + подпись + «как посчитано» (tooltip) | с дельтой, с «неполные данные» (`text-warning`) | `NumberTicker` на motion |
| Chart | recharts/evilcharts, цвета `cssVar('chart-1…5')` | line, bar, heatmap (B-08) | появление линии, без бесконечных анимаций |
| **FileList** (пакет) | `Table`: строка файла (иконка формата, имя только для показа, версия, стадия) → дочерние строки документов (вид, страницы, уверенность — полоска + число) | норм / сомнительный (`bg-warning/10`, сверху) / отклонён (`text-destructive` + подсказка) / UNREADABLE | раскрытие сегментов — `layout`, смена стадии — crossfade `tween.fast`, текущая стадия — пульс `opacity` |
| **DocumentViewer** (пакет) | `bg-muted rounded-lg`, страница на canvas, панель зум/поворот/страницы `IconButton`; bbox — `ring-2 ring-ring` + `bg-primary/10` | PDF / изображение, одна/много страниц | зум/пан — `react-zoom-pan-pinch`, фокус bbox — `spring.soft` |
| **FieldsPanel** (пакет) | список: метка `text-xs`, значение `text-sm font-medium tabular-nums`, уверенность, проверки — `Badge` | обычное / «перепроверить» / исправлено (`Badge info`) | inline-редактирование, `Enter`/`Esc`, сохранено — `spring.bouncy` |
| **ProtocolPanel** (протокол) | `Card` с группами FAILED (`destructive`) / WARNING (`warning`) / DATA_MISSING (`info`) / PASSED (`success`, свёрнуто); у правила: текст, «ожидалось / фактически» `tabular-nums`, ссылки на документы | версия протокола, «устарел — идёт перепроверка» | обновление — `fadeUp` новых строк, счётчики `NumberTicker` |
| **CompletenessCard** (протокол) | кольцо `bg-brand-gradient-conic` + список пунктов чек-листа (есть / нет / условие не применимо) | полный (`brand`) / не хватает (`warning`) | рост кольца `tween.slow` |

## 6. Зоны
| Зона | Тема | Плотность | Особенности |
|---|---|---|---|
| `/specialist` | светлая + переключатель | `text-sm`, таблицы | сайдбар, фильтры в URL, страница пакета — две панели (документы и оригинал слева, поля и протокол справа, ≥ 1280 px), горячие клавиши, «как посчитано» у статистики, суммы `tabular-nums` по правому краю |

## 7. Иконки и изображения
- Основной набор — **Plasma Icons** (открытые иконки Сбера, MIT): SVG из `@salutejs/plasma-icons` копируем в `shared/ui/Icon` (без styled-components), с атрибуцией. Чего нет — `lucide-react` со `strokeWidth={1.75}`; анимированные — `@lucide-animated`. Размеры: `size-4` в тексте, `size-5` в кнопках, `size-6` в навигации.
- Картинки — только по запросу к человеку (реальные или генерация), webp ≤ 200 КБ, `loading="lazy"`; документы и ПДн никогда не в git/`public`.

## 8. Движение — как применять
Значения — раздел 2, правила и рецепты — `frontend-fsd/references/motion.md`. UI — motion; 3D, шейдеры и фоны — R3F/ogl (вход, пустые состояния, экран «комплект собран»); сложные таймлайны — GSAP; один движок на компонент (ADR-036). Кратко: UI-переходы `duration.fast`/`base`, крупные — `slow`, `hero` только для лендинга и слайдов. Вход — `ease.out`, уход короче и `ease.in`. Пружины — для нажатий, поповеров, шторок. Анимируем `transform`/`opacity`, уважаем `prefers-reduced-motion` (`MotionConfig reducedMotion="user"`). Ничего не крутится бесконечно, кроме индикатора текущей стадии.

## 9. Доступность
Контраст ≥ 4.5:1 проверяет генератор; фокус всегда виден (`ring-ring`); вся навигация с клавиатуры; `aria-label` у иконок-кнопок; цвет никогда не единственный носитель смысла (статус = цвет + иконка + текст).

## 10. Чек-лист дизайн-ревью экрана
- [ ] Только токены: `fsd-check` чистый, нет hex/arbitrary/литералов анимаций.
- [ ] Шкала текста и отступов по разделам 3–4; тач-цели по зоне.
- [ ] Состояния: loading / empty / error / offline / disabled / focus.
- [ ] Светлая и тёмная тема, 390×844 и 1440×900.
- [ ] Кастомные контролы, не нативные; движение по пресетам; reduced-motion.
- [ ] Тексты из i18n, на «вы», ошибка = что случилось + что делать.
