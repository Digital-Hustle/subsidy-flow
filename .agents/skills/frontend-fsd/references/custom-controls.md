# Кастомные контролы: всё своё, ничего «из коробки»

Цель: ни один элемент не выглядит как дефолтный shadcn/браузер. **Поведение и доступность** берём у примитивов (Radix через shadcn, Base UI, cmdk). **Вид и движение** — наши: тема Сбера (`sber-brand.md`) + Motion (`motion.md`).

## Порядок сборки компонента
1. Есть ли он уже в `shared/ui/`? Если да — расширь вариантом (`cva`), не создавай второй.
2. Возьми **анимированную основу** из реестра (таблица ниже): `npx shadcn@latest add @animate-ui/components-radix-dropdown-menu`. Без реестра (Animata, Uiverse) — скопируй исходник вручную.
3. Сразу после установки:
   - перенеси файл в `shared/ui/<Comp>/<Comp>.tsx`, экспорт именованный, `memo` для листовых компонентов;
   - замени цвета на токены, радиусы — на `rounded-lg/xl`, шрифт — `font-sans/display`;
   - убери `tw-animate-css`/`animate-in`/`data-[state=open]:animate-*` и `@keyframes` — анимации переписываются на `motion` с токенами из `shared/config/motion.ts`;
   - тянет `gsap/three/ogl/lenis` — можно (ADR-036), но не смешивай с motion в одном компоненте и не тащи `framer-motion`/`react-spring`;
   - тексты — через `t()`, `aria-*` и фокус не трогай (их даёт примитив);
   - в шапке файла оставь комментарий-атрибуцию: `// основа: @react-bits/Stepper (MIT+Commons Clause), переработано`.
4. Проверка: клавиатура (Tab/стрелки/Esc/Enter), скринридер (role/label), `prefers-reduced-motion`, тёмная тема, тач (цель ≥ 48 px).

## Карта контролов
| Контрол | Основа (поведение) | Откуда брать движение и вид | Где нужен |
|---|---|---|---|
| Button / IconButton | shadcn `button` | `press` из токенов. Акценты: `@magicui/shimmer-button`, `@aceternity/stateful-button` (загрузка → успех), `@react-bits/HoldButton` (удержание) | везде |
| Slide-to-confirm | свой (`drag="x"`) | `@react-bits/SlideCommit`, `animata button/swipe-button` | киоск: ручной проход охранника |
| Input / Field | shadcn `input` + `label` | плавающий лейбл, «тряска» при ошибке: `@smoothui/animated-input`, `@cult-ui/animated-field` | формы админки |
| OTP / TOTP | `input-otp` (MIT) | `@react-bits/CodeSlots`, `@smoothui/animated-o-t-p-input`, `@eldoraui/clerk-otp` | вход админа (TOTP), инвайт рабочего |
| Select | Radix Select (shadcn `select`) | `@cult-ui/animated-select`, `@cult-ui/halo-select`, `@smoothui/select`, `@react-bits/GlideSelect` | фильтры, формы |
| Combobox / автокомплит | `cmdk` + Popover (shadcn `command`) | `@smoothui/combobox`. Опции с сервера: `?q=` + debounce 200 мс + `useInfiniteQuery` | поиск рабочего, объекта, бригады |
| Multi-select (чипы) | cmdk + Popover | чипы через `layout` + `AnimatePresence mode="popLayout"`, `@smoothui/animated-tags` | роли, зоны допуска |
| Dropdown / Context menu | Radix DropdownMenu / ContextMenu | `@animate-ui/components-radix-dropdown-menu`, подсветка пункта `@motion-primitives/animated-background` | действия в таблицах |
| Popover | Radix Popover | `@animate-ui/components-radix-popover`, `@motion-primitives/morphing-popover` | быстрые формы, фильтры |
| Tooltip | Radix Tooltip | `@animate-ui/components-animate-tooltip`, `@react-bits/WarmTooltip`, `@aceternity/animated-tooltip` (аватары) | иконки, сокращения |
| Dialog / Alert dialog | Radix Dialog | `@animate-ui/components-radix-dialog`, `@motion-primitives/morphing-dialog` (из карточки), `@aceternity/animated-modal` | подтверждения, карточка рабочего |
| Sheet / Drawer / Bottom sheet | Radix Dialog | `@animate-ui/components-radix-sheet`, `@smoothui/drawer` (motion). `vaul` не берём — у него свои CSS-анимации | телефон рабочего, фильтры на мобиле |
| Tabs / Segmented | Radix Tabs | `layoutId`-индикатор: `@animate-ui/components-radix-tabs`, `@cult-ui/direction-aware-tabs`, animata `tabs/fluid-tabs`, `@react-bits/RubberSegment` | разделы админки, переключатель периода |
| Switch / Checkbox / Radio | Radix | `@animate-ui/components-radix-switch`/`-checkbox`/`-radio-group`, `@react-bits/SquishSwitch`, `@react-bits/SpringCheck`, `@react-bits/JellyRadio` | настройки, флаги |
| Slider / Range | Radix Slider | `@react-bits/ElasticSlider`, `@smoothui/exposure-slider` | порог похожести лица (только просмотр, порог задаёт сервер) |
| Date / Range picker | shadcn `calendar` (react-day-picker, MIT) | смена месяца — `AnimatePresence` со сдвигом по направлению, выбор диапазона — `layout` | табель, отчёты, смены |
| Time / Duration wheel | свой (`drag="y"` + снап) | `@smoothui/duration-picker`, `@react-bits/OptionWheel` (перевести на motion) | смены, перерывы |
| Number input / счётчик | свой | `@smoothui/animated-number-input`, `@motion-primitives/sliding-number`, `@animate-ui/primitives-texts-sliding-number` | лимиты, KPI |
| Stepper / онбординг | свой | `@react-bits/Stepper`, `@smoothui/animated-stepper`, `@cult-ui/onboarding` | активация устройства рабочего, заведение эталона лица |
| Scroll area / скроллбар | Radix ScrollArea | свой thumb `bg-border hover:bg-muted-foreground`, fade-края `@motion-primitives/progressive-blur`, прогресс `@magicui/scroll-progress` | таблицы, ленты, чат помощника |
| Виртуальный список | `@tanstack/react-virtual` | вставка строк из SSE: `AnimatePresence` + подсветка | журнал проходов, табель |
| Таблица | `@tanstack/react-table` (MIT) | липкая шапка, `layout` при сортировке, раскрытие строки `animate-ui primitives-effects-auto-height` | журнал, рабочие, смены |
| Toast | `react-hot-toast` (стек ADR-021) с кастомным рендером | свайп-закрытие `@react-bits/SwipeToast`, `@cult-ui/animated-toast` | все ответы мутаций |
| Accordion / Disclosure | Radix Accordion | `@animate-ui/components-radix-accordion`, `@motion-primitives/disclosure` | FAQ, детали попытки прохода |
| Sidebar / навигация | shadcn `sidebar` | `@animate-ui/components-radix-sidebar`, `@aceternity/sidebar`, `@motion-primitives/animated-background` | админка |
| Dock / FAB | свой | `@magicui/dock`, `@react-bits/Dock`, animata `fabs/speed-dial` | телефон рабочего |
| Skeleton | свой | шиммер на motion (`x: -100% → 100%`, `repeat: Infinity`), формы — animata `skeleton/*` | все состояния загрузки |
| Empty / Error state | свой | иллюстрация (по запросу), `fadeUp`, кнопка «Повторить» | все экраны |
| File upload (эталоны лица) | `react-dropzone` (MIT) | `@aceternity/file-upload`, `@smoothui/animated-file-upload` | заведение эталона |
| Иконки | `lucide-react` | `@lucide-animated/*` (motion): `check`, `scan-face`, `shield-check`, `lock`, `bell`, `refresh-cw`, `loader-circle`, `map-pin-check` | статусы, кнопки |

## Сценарии «Проходной» поверх контролов
- **Киоск**: один экран и минимум контролов — сканер, вердикт, slide-to-confirm, PIN охранника (OTP-слоты). Крупно, контрастно, без hover-эффектов.
- **Телефон рабочего (PWA)**: карточка-пропуск с QR, bottom sheet вместо модалок, свайпы, dock снизу, pull-to-refresh (`drag="y"` у списка).
- **Админка**: таблицы + фильтры (select/combobox/date-range), sidebar, command palette (`⌘K`, cmdk) по рабочим и объектам, дашборд-бенто.
