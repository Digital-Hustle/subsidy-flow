# Анимации: только Motion (`motion/react`)

Motion — это бывший Framer Motion (MIT). Пакет `motion`, импорт `from "motion/react"`. Пакет `framer-motion` не ставим.
**Политика (ADR-036):** весь UI-моушн — `motion/react`. Разрешены и приветствуются там, где дают вау-эффект: WebGL (`three`, `@react-three/fiber`, `@react-three/drei`, `@react-three/postprocessing`, `postprocessing`, `ogl`) — фоны, 3D-пропуск, сканер лица, графики; GSAP — для компонентов из реестров на нём и сложных таймлайнов; Lenis — плавный скролл длинных страниц. Устройства мощные, «облегчать» ради слабых не нужно, но FPS проверяем. В одном компоненте — один движок.
**Запрещены дубли** (`fsd-check`, `banned-import`): `framer-motion` (ставим `motion`), `react-spring`, `animejs`, `popmotion`. **CSS-анимации** (`@keyframes`, `animate-*`) — только внутри `shared/ui` в коде из реестров; в своём коде — motion. `transition-colors` для hover можно везде.

Значения — единый источник `../assets/tokens.ts` (`motion.*`, ADR-033) → пресеты `../assets/motion.ts` (кладём в `shared/config/tokens.ts` и `shared/config/motion.ts`). Свои числа `duration/ease/stiffness/delay` в компонентах не пишем — `fsd-check` (правило `motion-literal`) это ловит. CSS-переходы — классами `duration-fast|base|slow` и `ease-out|in|in-out|emphasized` из той же темы.

## 1. Провайдер (app)
```tsx
// app/providers/MotionProvider/MotionProvider.tsx
import { MotionConfig } from "motion/react";
import { tween } from "@/shared/config/motion";
export const MotionProvider = ({ children }: { children: React.ReactNode }) => (
  <MotionConfig reducedMotion="user" transition={tween.base}>
    {children}
  </MotionConfig>
);
```
`reducedMotion="user"` отключает трансформации у тех, кто это просит в ОС. Прозрачность и цвет остаются.

## 2. Принципы (по emil-design-eng и review-animations)
1. **У анимации есть смысл**: обратная связь, смена состояния, иерархия или рассказ. «Красиво» — не причина.
2. **Анимируем только `transform`, `opacity`, `filter`, `clipPath`.** Ширину и высоту — через `layout`, а не через `width`.
3. **Уход быстрее входа**: вход `duration.base/slow`, уход `duration.fast`.
4. **Прерываемость**: пружины для всего, что тянут пальцем или переключают часто (табы, свитчи, шторки).
5. **Origin-aware**: поповер растёт из триггера (`transformOrigin` = `var(--radix-popper-transform-origin)`).
6. **Бюджет киоска**: ни одна анимация в пути прохода не задерживает показ решения. Вердикт виден ≤ 100 мс после ответа, анимация идёт поверх. Без `backdrop-blur` на киоске (слабый планшет).
7. **Стаггер ≤ 40 мс на элемент и не больше 8 элементов**, дальше — без задержки.

## 3. Прокрутка
```tsx
// Появление секций при прокрутке
<motion.section {...inView}>…</motion.section>

// Прогресс-бар чтения/прокрутки контейнера
const ref = useRef<HTMLDivElement>(null);
const { scrollYProgress } = useScroll({ container: ref });
const scaleX = useSpring(scrollYProgress, spring.soft);
<motion.div style={{ scaleX }} className="fixed inset-x-0 top-0 h-1 origin-left bg-brand-gradient" />

// Параллакс и «липкий стек» карточек на motion (GSAP ScrollTrigger — если нужен сложный пин-таймлайн)
const target = useRef<HTMLDivElement>(null);
const { scrollYProgress: p } = useScroll({ target, offset: ["start start", "end end"] });
const y = useTransform(p, [0, 1], [0, -120]);
const scale = useTransform(p, [0, 1], [1, 0.92]);
// каждая карточка: className="sticky top-24", style={{ scale }}

// Горизонтальная лента при вертикальной прокрутке
const x = useTransform(p, [0, 1], ["0%", "-66%"]);
<section ref={target} className="h-[300vh]"><div className="sticky top-0 h-screen overflow-hidden"><motion.div style={{ x }} className="flex gap-6">…</motion.div></div></section>

// Шапка, которая прячется при прокрутке вниз
const { scrollY } = useScroll();
useMotionValueEvent(scrollY, "change", (v) => setHidden(v > (scrollY.getPrevious() ?? 0) && v > 80));
```
Кастомный скролл-контейнер: Radix `ScrollArea` (через shadcn) + свой thumb. Длинные списки (журнал проходов) — `@tanstack/react-virtual` (MIT). Новые строки из SSE въезжают через `AnimatePresence` + `layout` и подсвечиваются на 1.2 с.

## 4. Свайпы и жесты
```tsx
// Свайп строки (действия в списке у телефона рабочего / журнала)
<motion.li
  drag="x" dragConstraints={{ left: 0, right: 0 }} dragElastic={0.2}
  onDragEnd={(_, i) => {
    if (i.offset.x < -swipe.offset || i.velocity.x < -swipe.velocity) onAction();
  }}
/>

// «Сдвиньте, чтобы подтвердить» — ручной проход охранника (защита от случайного нажатия)
const x = useMotionValue(0);
const progress = useTransform(x, [0, trackWidth - knob], [0, 1]);
<motion.div drag="x" dragConstraints={{ left: 0, right: trackWidth - knob }} dragElastic={0} dragMomentum={false} style={{ x }}
  onDragEnd={() => (progress.get() > swipe.confirmRatio ? onConfirm() : animate(x, 0, spring.snappy))} />
// На сервер уходит запрос; решение и запись в журнал — на бэке (rules.md §1.1).

// Bottom sheet: тянется вниз и закрывается
<motion.div drag="y" dragConstraints={{ top: 0 }} dragElastic={{ top: 0, bottom: 0.4 }}
  onDragEnd={(_, i) => (i.offset.y > swipe.offset || i.velocity.y > swipe.velocity) && onClose()} transition={spring.sheet} />

// Карусель со снапом (брифинги по технике безопасности): drag="x" + animate(x, -index*width, spring.soft)
// Стопка карточек «смахни, если ознакомлен» — по образцу @react-bits/Stack (motion)
```
Пороги свайпов только из `swipe` в токенах. На тач-устройствах задавай `touch-action: pan-y` у горизонтальных свайпов, чтобы не ломать вертикальную прокрутку.

## 5. Layout и общие элементы
```tsx
// Индикатор активного таба / сегмента
{active && <motion.span layoutId="tab-indicator" className="absolute inset-0 rounded-lg bg-card shadow-card" transition={spring.snappy} />}

// Карточка → детальная (рабочий в списке → профиль)
<motion.img layoutId={`avatar-${id}`} />   // в списке и на детальной один и тот же layoutId

// Смена экранов зоны
<AnimatePresence mode="wait" initial={false}>
  <motion.main key={location.pathname} variants={fadeUp} initial="hidden" animate="show" exit="exit" />
</AnimatePresence>
```
Для списка с удалением используй `AnimatePresence mode="popLayout"` и `layout` у элементов.

## 6. Сценарии «Проходной»
| Экран | Анимация | Как |
|---|---|---|
| Киоск, ожидание | мягкая пульсация рамки сканера и подсказки «Поднесите QR» | `@magicui/ripple` или свой `animate` с `repeat: Infinity`, амплитуда ≤ 4 % |
| Киоск, проверка лица | шаги «Ищем лицо → Живость → Сверка с эталоном» | `@aceternity/multi-step-loader` по `checks[]` из ответа сервера (`backend-spring/references/frontend-contract.md` §6), а не по таймеру |
| Киоск, решение | заливка экрана цветом из центра + отрисовка галочки/крестика + 3-секундный прогресс автосброса | `clipPath: circle(0% → 150%)`, `pathLength 0 → 1` с `spring.bouncy`, полоса `scaleX 1 → 0` за 3 с |
| Телефон, QR | кольцо 30-секундного окна, мягкая смена кода | SVG-круг `pathLength` от серверного смещения часов; новый QR — `AnimatePresence` с `blur` + `scale` |
| Телефон, пропуск | «голографическая» карточка, наклон за пальцем/гироскопом | `@eldoraui/holographic-card` или `@react-bits/TiltedCard` (motion) |
| Админка, дашборд | счётчики «на объекте», живая лента проходов, бенто-сетка | `@magicui/number-ticker` / `@motion-primitives/sliding-number`, `@magicui/animated-list`, `@magicui/bento-grid` |
| Админка, помощник | поле ввода с исчезающими подсказками, печать ответа, источники | `@aceternity/placeholders-and-vanish-input`, `@motion-primitives/text-effect` |
| Везде | тосты со свайпом, тултипы, поповеры из триггера | см. `custom-controls.md` |

Числа в анимированных счётчиках — только из API (rules.md §1.5). Анимация не придумывает промежуточных значений для отчётов: тикер стартует с прошлого реального значения.

## 7. Проверка
- Перед PR прогони `review-animations` (скилл) по изменённым компонентам.
- DevTools → Rendering → «Emulate prefers-reduced-motion: reduce»: интерфейс должен остаться рабочим.
- Performance на 4× CPU throttling: на киоске и телефоне без long tasks > 50 мс во время анимаций.
