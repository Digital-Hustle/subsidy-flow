# Каталог источников UI: что и откуда берём

Сводка по ресерчу (10.2026). Все реестры прописаны в `../assets/components.json`, MCP-серверы — в `.mcp.json` и `.cursor/mcp.json` в корне репо. Поиск компонентов делается **через MCP shadcn** (`search_items_in_registries`, `view_items_in_registries`, `get_add_command_for_items`). Сайты без MCP (21st.dev, демо, Dribbble-подобные) разбираем по разделу «Визуальные референсы» ниже.

## Правила отбора
1. **Движки (ADR-036).** Перед `add` смотри `dependencies` (`npx shadcn@latest view @ns/name`). `motion`, `three`/R3F, `ogl`, `gsap`, `lenis` — можно; `framer-motion` → меняем импорт на `motion/react`; `@react-spring/*`, `animejs` — переписываем на motion. `matter-js` и прочие физдвижки — по отдельному решению.
2. **Лицензии** (ADR-031): MIT / ISC / Apache — свободно. Aceternity License и MIT + Commons Clause (React Bits, Animate UI) — можно в нашем продукте, **нельзя** выкладывать сами компоненты отдельно как библиотеку. Uiverse и 21st — смотри лицензию конкретного компонента.
3. **Атрибуция** в шапке файла: откуда основа и что переделано.
4. После установки компонент проходит чек-лист из `custom-controls.md` («Сразу после установки»).

## Библиотеки
| Источник | Лицензия | Движок | Как подключать | Что берём (лучшее для кейса) |
|---|---|---|---|---|
| **shadcn/ui** | MIT | Radix | `@shadcn`, MCP `shadcn` | Основа всех примитивов: button, input, select, command, popover, dialog, sheet, tabs, table, calendar, sidebar, scroll-area, form |
| **Animate UI** (animate-ui.com) | MIT + Commons Clause | motion | `@animate-ui` | **Анимированные Radix-примитивы** — главный источник для кастомных dropdown/popover/dialog/sheet/tabs/switch/checkbox/radio/accordion/tooltip/sidebar. Тексты: sliding-number, counting-number, rolling, shimmering, typing. Эффекты: auto-height, highlight, magnetic, tilt |
| **Motion Primitives** | MIT | только motion | `@motion-primitives` | morphing-dialog, morphing-popover, transition-panel, animated-background (подсветка ховера), text-effect, text-morph, sliding-number, in-view, infinite-slider, progressive-blur, scroll-progress, image-comparison, tilt, magnetic, border-trail, glow-effect, disclosure, accordion, carousel, toolbar-dynamic |
| **Magic UI** | MIT | motion | `@magicui`, MCP `magicui` | number-ticker, animated-list (живая лента проходов), bento-grid (дашборд), border-beam и shine-border (активная карточка, киоск online), magic-card (spotlight-ховер), blur-fade, text-animate, word-rotate, animated-circular-progress-bar (заполненность объекта, окно QR), ripple (ожидание на киоске), dot/grid-pattern и flickering-grid (фоны), animated-beam (схема «киоск → access → face» на слайдах), scroll-progress, iphone/safari (макеты для презентации), confetti — **нет** |
| **Aceternity UI** | Aceternity License (бесплатно для конечных продуктов) | motion | `@aceternity` | multi-step-loader (этапы проверки лица), placeholders-and-vanish-input (ввод помощника), stateful-button, file-upload (эталоны), timeline (история рабочего и смены), sidebar, animated-modal, animated-tooltip (аватары бригады), card-spotlight, glowing-effect, hover-border-gradient, spotlight-new / background-beams / aurora-background (логин), sticky-scroll-reveal / container-scroll-animation / tracing-beam (лендинг и демо-страница), encrypted-text (подпись QR), apple-cards-carousel (брифинги). **Не берём**: 3d-globe, world-map, шейдеры (three) |
| **React Bits** | MIT + Commons Clause | motion / gsap / ogl / three | `@react-bits` (имя `<Comp>-TS-TW`) | Берём всё, включая WebGL-фоны и GSAP-эффекты (ADR-036). Motion: Stepper, Stack (карточки-брифинги свайпом), SlideCommit, SwipeRow, SwipeToast, CodeSlots (OTP), CountUp, Counter, AnimatedList, Dock, ElasticSlider, TiltedCard, FlipCard, TearTicket (пропуск-билет), SquishSwitch, SpringCheck, JellyRadio, RubberSegment, StatusMark, BellToggle, WarmTooltip, Carousel, BlurText, ShinyText, GradientText, RotatingText, DecryptedText, TrueFocus, PromptBar, ThoughtLine. Без зависимостей: SpotlightCard, GlareHover, StarBorder, BorderGlow, HoldButton, SloshGauge, LatticeLoader, OptionWheel, GradualBlur, GlassSurface. **Запрещены** (~60 % каталога): всё на gsap / ogl / three — Aurora, Silk, Galaxy, SplitText, ScrollReveal, MagicBento, CardSwap и др. |
| **Eldora UI** | MIT | motion | `@eldoraui` | holographic-card (пропуск рабочего), live-button (статус киоска), animated-badge, card-flip-hover, clerk-otp, text-эффекты (blur-in, word/letter-pull-up, gradual-spacing), terminal, logo-timeline, orbit-rotation, макеты iphone-17-pro / macbook-pro / browser для презентации. **Не берём**: cobe-globe, novatrix (ogl), three |
| **Animata** | MIT | motion | без реестра, копировать из `github.com/codse/animata/tree/main/animata` | **Графики на чистом SVG + motion**: graphs/bar-chart, donut-chart, gauge-chart, ring-chart, progress — для KPI-плиток. list/transaction-list и list/avatar-list (лента проходов, бригада), card/notification-card, card/score-card, card/swap-card, tabs/fluid-tabs, tabs/gooey-tabs, container/nav-tabs, fabs/speed-dial, skeleton/* (list, report, wide-card), progress/animatedtimeline, button/swipe-button и button/status-button, preloader/vertical-tiles |
| **Cult UI** (бонус из реестра shadcn) | MIT | motion | `@cult-ui` | animated-select / halo-select, animated-input / -field, animated-tabs, direction-aware-tabs, animated-toast, animated-number, dynamic-island (статус киоска: «Онлайн · 12 проходов»), floating-panel, side-panel, expandable, sortable-list, family-drawer, timer, onboarding. **Не берём**: всё на `@paper-design/shaders-react` |
| **Smooth UI** (бонус) | MIT | motion | `@smoothui` | select, combobox, drawer, duration-picker, animated-number-input, animated-o-t-p-input, animated-stepper, animated-tags, animated-file-upload, basic-toast, **ai-*** (ai-message, ai-reasoning, ai-sources, ai-tool-call, ai-loader — чат помощника с инструментами и источниками) |
| **Evil Charts** (бонус) | MIT | recharts + motion | `@evilcharts` | recharts-area/bar/line/radial/pie-chart с анимациями — для аналитики админки (recharts уже в стеке). `echarts-*` **не берём** (лишний движок) |
| **Lucide Animated** (бонус) | MIT | motion | `@lucide-animated` | анимированные иконки статусов: check, scan-face, shield-check, lock/lock-open, bell, refresh-cw, loader-circle, map-pin-check, clock |
| **Uiverse** (uiverse.io) | MIT (сообщество) | HTML + CSS / Tailwind | вручную с сайта | **Только как визуальный референс**: кнопки, лоадеры, тогглы, чекбоксы, инпуты, карточки. Переносим форму на Tailwind-токены, движение — на motion. В комментарии автор и ссылка. Сторонний `uiverse-mcp` (1★, не официальный) **не подключаем** |
| **21st.dev** | у каждого компонента своя | разное | без MCP (ключ платный): открываем сайт в браузере, `npx shadcn@latest add "https://21st.dev/r/<author>/<name>"`, если компонент публичный | Поиск по 10 000+ компонентов: смотрим превью, скриншотим, читаем код, повторяем на токенах. Перед установкой проверяй лицензию и deps |
| **Designeer** (designeer.xyz) | каталог | — | WebMCP (только в браузере, для IDE-агентов не годится) | Референсы до верстки: **Supahero** (hero), **navbar.design / footer.design**, **Details** (разбор микровзаимодействий), **Sombra / Dark Mode Design** (тёмная тема), **SaaSFrame** (SaaS и дашборды), **Pttrns / Appinspo / Scrnshts** (мобильный рабочий), **Loader Buttons**, **Backgrounds Supply**, **Refactoring UI** (обязательно к прочтению F1/F2). Инструменты: OKLCH, Huetone, Utopia (fluid-типографика), Color.review (контраст) |

## Подбор по экранам
| Экран | Состав |
|---|---|
| Логин админки | фон `@aceternity/spotlight-new` или `@magicui/flickering-grid` в зелёных тонах, карточка с `@magicui/border-beam` по мастер-градиенту, TOTP — `@react-bits/CodeSlots` |
| Дашборд «Обстановка» | `@magicui/bento-grid`; плитки: `@magicui/number-ticker` «на объекте», animata `gauge-chart` заполненность, `@evilcharts/recharts-area-chart` проходы по часам, `@magicui/animated-list` живая лента из SSE, `@cult-ui/dynamic-island` статус киосков |
| Журнал проходов | TanStack Table + virtual, фильтры (`@cult-ui/animated-select`, `@smoothui/combobox`, date-range), раскрытие строки `@animate-ui` auto-height, причина отказа — бейдж с иконкой `@lucide-animated` |
| Карточка рабочего | `@motion-primitives/morphing-dialog` из строки таблицы, `@aceternity/timeline` история, `@aceternity/file-upload` эталоны (3–5 фото) |
| Помощник | `@aceternity/placeholders-and-vanish-input`, `@smoothui/ai-message` / `ai-sources` / `ai-tool-call` (видно, какие инструменты дали числа), печать — `@motion-primitives/text-effect` |
| Киоск | крупный сканер с `@magicui/ripple`, `@aceternity/multi-step-loader` по `checks[]` из ответа сервера, полноэкранный вердикт (см. `motion.md` §6), `@react-bits/SlideCommit` для ручного прохода |
| Телефон: пропуск | `@eldoraui/holographic-card` / `@react-bits/TearTicket`, кольцо окна QR — `@magicui/animated-circular-progress-bar`, dock `@magicui/dock` |
| Телефон: брифинги | `@react-bits/Stack` (смахнуть = ознакомлен, подтверждение уходит на сервер), `@aceternity/apple-cards-carousel` |
| Демо и слайды | `@magicui/animated-beam` (архитектура), `@eldoraui/iphone-17-pro` / `@magicui/safari` (макеты), `@aceternity/sticky-scroll-reveal` (история продукта), `@magicui/text-animate` |

## Команды
```bash
cd subsidy/frontend
npx shadcn@latest view @magicui/number-ticker          # код и deps до установки
npx shadcn@latest add @animate-ui/components-radix-dropdown-menu
npx shadcn@latest add @react-bits/Stepper-TS-TW
npx shadcn@latest search @aceternity -q loader          # поиск без MCP
```

## Визуальные референсы (21st.dev, демо, чужие сайты)
Текста и кода мало — движение и пропорции видно только глазами.
1. Открой страницу в браузере (Playwright / agent-browser), дождись загрузки, закрой баннеры.
2. Сними скриншоты: общий вид, крупно сам компонент, состояния (hover, focus, open, loading, error), мобильную ширину 390 px. Для анимаций — 3–5 кадров по ходу или короткое видео.
3. Разбери скриншоты зрением (vision): сетка, отступы, радиусы, иерархия текста, слои, тени, как ведёт себя движение. Прочитай доступный код/DOM/CSS, чтобы понять механику.
4. Повтори на наших токенах (`tokens.ts`) и пресетах motion — не копируй чужие hex и пиксели.
5. Сравни свой результат скриншотом рядом с референсом и доведи. В PR — ссылка на референс и что взято.
Не копируй защищённые логотипы и фото; лицензию кода проверяй до копирования.
