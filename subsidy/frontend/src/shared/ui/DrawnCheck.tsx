import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { popIn, tween } from "@/shared/config/motion";

/**
 * Галочка успеха (ADR-048): круг появляется пружиной popIn, затем галочка прорисовывается по контуру (pathLength).
 * Цвет — через className (bg-*, text-*), размер — size-*.
 */
export const DrawnCheck = ({ className, delay = tween.fast.duration }: { className?: string; delay?: number }) => (
  <motion.span variants={popIn} initial="hidden" animate="show" exit="exit" className={cn("flex items-center justify-center rounded-full", className)}>
    <svg viewBox="0 0 24 24" aria-hidden className="size-1/2 overflow-visible">
      <motion.path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
        initial={{ pathLength: 0, opacity: 0 }} animate={{ pathLength: 1, opacity: 1 }} transition={{ pathLength: { ...tween.draw, delay }, opacity: { ...tween.instant, delay } }} />
    </svg>
  </motion.span>
);
