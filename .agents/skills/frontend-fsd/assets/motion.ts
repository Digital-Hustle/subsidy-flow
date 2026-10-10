// Пресеты анимаций «Проходной». Кладётся в src/shared/config/motion.ts (во время хакатона).
// Все числа — из ./tokens (ADR-033); здесь только сборка пресетов. Анимации только motion/react (ADR-031).
// В компонентах: transition={spring.snappy}, variants={fadeUp}, {...press} — никаких литералов duration/ease.
import type { Transition, Variants } from "motion/react";
import { motion as m } from "./tokens";

export const ease = m.ease;
export const duration = m.duration;
export const spring = m.spring satisfies Record<string, Transition>;
export const swipe = m.swipe;

/** Готовые переходы по длительности: transition={tween.fast} */
export const tween = {
  instant: { duration: m.duration.instant, ease: m.ease.out },
  fast: { duration: m.duration.fast, ease: m.ease.out },
  base: { duration: m.duration.base, ease: m.ease.out },
  slow: { duration: m.duration.slow, ease: m.ease.emphasized },
  exit: { duration: m.duration.fast, ease: m.ease.in },
} as const satisfies Record<string, Transition>;

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: m.distance.enter, filter: `blur(${m.distance.blur}px)` },
  show: { opacity: 1, y: 0, filter: "blur(0px)", transition: tween.slow },
  exit: { opacity: 0, y: -m.distance.exit, transition: tween.exit },
};

export const scaleIn: Variants = {
  hidden: { opacity: 0, scale: m.scale.popIn },
  show: { opacity: 1, scale: 1, transition: m.spring.snappy },
  exit: { opacity: 0, scale: m.scale.popOut, transition: tween.exit },
};

export const stagger = (step: number = m.stagger.step, delay = 0): Variants => ({
  hidden: {},
  show: { transition: { staggerChildren: Math.min(step, m.stagger.max), delayChildren: delay } },
});

/** Нажатие для любых кликабельных элементов: <motion.button {...press}> */
export const press = {
  whileHover: { scale: m.scale.hover },
  whileTap: { scale: m.scale.tap },
  transition: m.spring.press,
} as const;

/** Появление при прокрутке: <motion.section {...inView}> */
export const inView = {
  initial: "hidden",
  whileInView: "show",
  viewport: { once: true, amount: 0.3 },
  variants: fadeUp,
} as const;
