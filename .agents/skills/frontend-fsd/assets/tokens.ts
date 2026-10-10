// ЕДИНСТВЕННЫЙ источник дизайн-токенов «Проходной» (ADR-033).
// Во время хакатона копируется в subsidy/frontend/src/shared/config/tokens.ts.
// Из него генерируются: src/app/styles/theme.css (Tailwind 4 @theme + shadcn-переменные)
// и таблицы в frontend/docs/DESIGN.md — командой `npm run gen:tokens`
// (node --experimental-strip-types .../scripts/gen-tokens.mjs). Руками theme.css не правим.
// Motion берёт значения отсюда же через src/shared/config/motion.ts.
//
// Обязательное от Сбера — фирменный зелёный и SB Sans (ADR-036). Остальное — наша современная система:
// стартовые значения из публичной части Nova (шкала цветов, кнопка #148F2B, типографика), Plasma (MIT),
// радиусы и прочее — свои. Меняем свободно, но только здесь.
// Только синтаксис, который стирается при strip-types: никаких enum/namespace/импортов.

/** Шкалы (сырые цвета). В компонентах НЕ используются — только через semantic. */
export const palette = {
  white: "#FFFFFF",
  black: "#080808",
  green: {
    950: "#0A2B10", 900: "#0A4014", 800: "#095C18", 700: "#0D8523", 600: "#148F2B",
    500: "#21A038", 400: "#24B23E", 300: "#15D13B", 200: "#2AE853", 100: "#62F582", 50: "#E0FFE9",
  },
  gray: {
    1000: "#080808", 950: "#171717", 900: "#262626", 850: "#363636", 800: "#4E4E4E",
    700: "#707070", 650: "#7A7A7A", 600: "#858585", 500: "#B2B2B2", 400: "#C7C7C7",
    300: "#DCDCDC", 200: "#E8E8E8", 100: "#F5F5F5", 50: "#F9F9F9",
  },
  orange: { 800: "#B54300" /* наш: #D14D00, затемнённый до AA */, 700: "#D14D00", 600: "#E35502", 500: "#F55D05", 400: "#FA6D20" },
  red: { 700: "#E31227", 500: "#F31B31", 400: "#FF3D51" },
  blue: { 800: "#0C72B6" /* Plasma */, 700: "#067DCC", 500: "#078BE4", 400: "#199AF0" },
  teal: { 700: "#0A8A66", 600: "#05996F", 500: "#0C8688", 400: "#0C9597" },
  purple: { 700: "#722BA1" },
  /** Мастер-цвета брендбука Сбера: только иллюстрации, градиент, графики */
  sber: {
    green: "#21A038", sky: "#00ADEE", blue: "#0087CD", arctic: "#42E3B4", spring: "#A0E720", sun: "#FAED00",
  },
} as const;

const p = palette;

/**
 * Семантические цвета → CSS-переменные `--<name>` и классы `bg-<name>`, `text-<name>` и т. д.
 * Пара `x` / `x-foreground` проверяется генератором на контраст WCAG (≥ 4.5:1),
 * исключения перечислены в `contrastLargeTextOnly` с причиной.
 */
export const semantic = {
  light: {
    background: p.gray[50],
    foreground: p.gray[950],
    card: p.white,
    "card-foreground": p.gray[950],
    popover: p.white,
    "popover-foreground": p.gray[950],
    primary: p.green[600],               // кнопка Nova
    "primary-foreground": p.white,
    "primary-hover": p.green[700],
    brand: p.green[500],                 // «Зелёный СБЕР»: плоскости, иконки, крупный текст
    "brand-foreground": p.white,
    secondary: p.gray[200],
    "secondary-foreground": p.gray[950],
    muted: p.gray[100],
    "muted-foreground": p.gray[700],
    accent: p.green[50],
    "accent-foreground": p.green[800],
    destructive: p.red[700],
    "destructive-foreground": p.white,
    warning: p.orange[800],
    "warning-foreground": p.white,
    info: p.blue[800],
    "info-foreground": p.white,
    border: p.gray[300],
    input: p.gray[300],
    ring: p.green[500],
    overlay: "rgb(8 8 8 / 0.5)",
    // результат прохода на киоске: цвет по коду из ответа access, не по логике клиента
    "decision-allow": p.green[500],
    "decision-allow-foreground": p.white,
    "decision-deny": p.red[700],
    "decision-deny-foreground": p.white,
    "decision-manual": p.orange[700],
    "decision-manual-foreground": p.white,
    "decision-error": p.gray[800],
    "decision-error-foreground": p.white,
    "chart-1": p.sber.green,
    "chart-2": p.sber.sky,
    "chart-3": p.sber.arctic,
    "chart-4": p.sber.blue,
    "chart-5": p.sber.spring,
  },
  dark: {
    background: p.gray[1000],
    foreground: p.gray[100],
    card: p.gray[950],
    "card-foreground": p.gray[100],
    popover: p.gray[950],
    "popover-foreground": p.gray[100],
    primary: p.green[400],
    "primary-foreground": p.black,
    "primary-hover": p.green[300],
    brand: p.green[400],
    "brand-foreground": p.black,
    secondary: p.gray[900],
    "secondary-foreground": p.gray[100],
    muted: p.gray[900],
    "muted-foreground": p.gray[500],
    accent: p.green[950],
    "accent-foreground": p.green[100],
    destructive: p.red[400],
    "destructive-foreground": p.black,
    warning: p.orange[400],
    "warning-foreground": p.black,
    info: p.blue[400],
    "info-foreground": p.black,
    border: p.gray[850],
    input: p.gray[850],
    ring: p.green[400],
    overlay: "rgb(0 0 0 / 0.7)",
    "decision-allow": p.green[400],
    "decision-allow-foreground": p.black,
    "decision-deny": p.red[400],
    "decision-deny-foreground": p.black,
    "decision-manual": p.orange[400],
    "decision-manual-foreground": p.black,
    "decision-error": p.gray[700],
    "decision-error-foreground": p.white,
    "chart-1": p.green[400],
    "chart-2": p.sber.sky,
    "chart-3": p.sber.arctic,
    "chart-4": p.blue[400],
    "chart-5": p.sber.spring,
  },
} as const;

/** Пары с контрастом < 4.5:1, допустимые только для крупного текста (≥ 19px semibold / 24px). */
export const contrastLargeTextOnly = {
  primary: "Nova: кнопка #148F2B с белым текстом (≈4.2:1). Текст кнопок — semibold 16px+; мелкий зелёный текст — только text-accent-foreground",
  brand: "«Зелёный СБЕР» #21A038 с белым ≈3.4:1 — только плоскости, иконки и текст ≥ 19px semibold; кнопки — primary",
  "decision-allow": "экран вердикта киоска: текст ≥ 48px",
  "decision-manual": "экран вердикта киоска: текст ≥ 48px",
} as const;

export const gradient = {
  brand: `linear-gradient(90deg, ${p.sber.sun} 0%, ${p.sber.spring} 22%, ${p.sber.green} 48%, ${p.sber.arctic} 68%, ${p.sber.sky} 84%, ${p.sber.blue} 100%)`,
  "brand-conic": `conic-gradient(from 200deg, ${p.sber.blue}, ${p.sber.sky}, ${p.sber.arctic}, ${p.sber.green}, ${p.sber.spring}, ${p.sber.sun}, ${p.sber.blue})`,
} as const;

export const font = {
  sans: `"SB Sans Text", "SBSansText", system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif`,
  display: `"SB Sans Display", "SBSansDisplay", "SB Sans Text", system-ui, sans-serif`,
  mono: `ui-monospace, "JetBrains Mono", "SFMono-Regular", Menlo, monospace`,
} as const;

/** CSS со шрифтами SB Sans (CDN Сбера, как в Plasma). Файлы шрифтов в git не кладём. */
export const fontImports = [
  "https://cdn-app.sberdevices.ru/shared-static/0.0.0/styles/SBSansText.0.2.0.css",
  "https://cdn-app.sberdevices.ru/shared-static/0.0.0/styles/SBSansDisplay.0.2.0.css",
] as const;

/**
 * Типографика: шкала Nova (px). Имена совпадают с Tailwind (text-sm …), чтобы shadcn-компоненты
 * сразу попадали в шкалу Сбера; дефолтные размеры Tailwind отключены.
 */
export const text = {
  xs:   { size: 13, lineHeight: 16, role: "подписи, бейджи, легенды графиков" },
  sm:   { size: 15, lineHeight: 20, role: "основной текст админки, ячейки таблиц, инпуты" },
  base: { size: 16, lineHeight: 24, role: "основной текст телефона, абзацы" },
  lg:   { size: 19, lineHeight: 24, role: "заголовок карточки, h4" },
  xl:   { size: 22, lineHeight: 28, role: "заголовок секции, h3" },
  "2xl": { size: 28, lineHeight: 36, role: "заголовок страницы, h2" },
  "3xl": { size: 34, lineHeight: 40, role: "h1 админки, крупные KPI" },
  "4xl": { size: 37, lineHeight: 44, role: "h1 лендинга/телефона" },
  "5xl": { size: 48, lineHeight: 56, role: "вердикт киоска: причина (наше, не Nova)" },
  "6xl": { size: 64, lineHeight: 72, role: "вердикт киоска: ПРОХОДИТЕ / ОТКАЗ (наше, не Nova)" },
} as const;

export const fontWeight = { regular: 400, medium: 500, semibold: 600, bold: 700 } as const;

/** Сетка 4 px. Tailwind: --spacing = 4px, поэтому p-4 = 16px. */
export const spacingBase = 4;
/** Разрешённые шаги для p/m/gap/space/inset (проверяет fsd-check, правило spacing-scale). */
export const spacingSteps = [0, 0.5, 1, 2, 3, 4, 5, 6, 8, 10, 12, 16] as const; // 0 2 4 8 12 16 20 24 32 40 48 64 px

/** Высоты контролов Nova → классы h-control-md, min-h-control-lg и т. п. */
export const control = { xs: 32, sm: 40, md: 48, lg: 56, xl: 64 } as const;

/** Радиусы (px), современные: чипы 10, кнопки/инпуты 14, карточки 20, шторки/модалки 28, крупные плитки 36. */
export const radius = { xs: 6, sm: 10, md: 14, lg: 20, xl: 28, "2xl": 36 } as const;

export const shadow = {
  light: {
    xs: "0 1px 2px rgb(38 38 38 / 0.04)",
    sm: "0 2px 4px rgb(38 38 38 / 0.16), 0 1px 2px rgb(38 38 38 / 0.04)",
    card: "0 1px 2px rgb(38 38 38 / 0.04), 0 8px 16px rgb(38 38 38 / 0.16)",
    pop: "0 12px 40px -12px rgb(8 8 8 / 0.25)",
  },
  dark: {
    xs: "0 1px 2px rgb(0 0 0 / 0.4)",
    sm: "0 2px 4px rgb(0 0 0 / 0.5)",
    card: "0 1px 0 rgb(255 255 255 / 0.04) inset, 0 8px 24px -8px rgb(0 0 0 / 0.6)",
    pop: "0 16px 48px -12px rgb(0 0 0 / 0.8)",
  },
} as const;

/** Слои по оси Z → классы z-dropdown, z-modal … */
export const zIndex = { base: 0, sticky: 10, dropdown: 40, overlay: 50, modal: 60, toast: 70, kiosk: 80 } as const;

/** Движение. Секунды — как ожидает motion; генератор переводит в ms для CSS-переходов. */
export const motion = {
  ease: {
    out: [0.22, 1, 0.36, 1],      // вход элементов, раскрытие
    in: [0.4, 0, 1, 1],           // уход элементов (короче входа)
    inOut: [0.65, 0, 0.35, 1],    // перемещение между состояниями
    emphasized: [0.2, 0, 0, 1],   // крупные переходы экранов
  },
  duration: {
    instant: 0.1,  // нажатие
    fast: 0.2,     // hover, тултип, чекбокс (переходы Nova .2s)
    base: 0.3,     // поповер, селект, тост
    slow: 0.4,     // модалка, шторка, смена экрана (анимации Nova .4s)
    hero: 0.6,     // только лендинг/презентация, не в пути прохода
  },
  spring: {
    press: { type: "spring", stiffness: 600, damping: 30, mass: 0.6 },   // кнопки, свитчи
    snappy: { type: "spring", stiffness: 420, damping: 34 },            // поповеры, индикатор табов
    soft: { type: "spring", stiffness: 240, damping: 28 },              // карточки, layout
    sheet: { type: "spring", stiffness: 320, damping: 36, mass: 0.9 },  // bottom sheet, drawer
    bouncy: { type: "spring", bounce: 0.35, visualDuration: 0.45 },     // успех (галочка ALLOW)
  },
  stagger: { step: 0.04, max: 0.3 },
  distance: { enter: 12, exit: 8, blur: 4 },  // px сдвига/размытия при появлении
  scale: { hover: 1.02, tap: 0.97, popIn: 0.96, popOut: 0.98 },
  swipe: { offset: 80, velocity: 500, confirmRatio: 0.85 },  // px и px/s
  kiosk: { resultHoldMs: 4000, challengeTimeoutMs: 10000 }, // автосброс экрана вердикта, окно челленджа
  toast: { durationMs: 4000, errorDurationMs: 6000 },        // react-hot-toast
} as const;

/** Ссылка на CSS-переменную для мест, где нужен цвет строкой (recharts, canvas, SVG). */
export const cssVar = (name: keyof typeof semantic.light) => `var(--${name})` as const;

export type SemanticColor = keyof typeof semantic.light;
