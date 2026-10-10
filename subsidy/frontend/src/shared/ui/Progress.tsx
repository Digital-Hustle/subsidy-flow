import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { spring } from "@/shared/config/motion";

/**
 * Полоса заполнения на пружине. tone: brand — фирменный градиент, white — на фирменной заливке.
 * Заливка сдвигается translateX, а не scaleX: так WebKit не рвёт скругление (ADR-045).
 */
export const Progress = ({ value, className, tone = "brand" }: { value: number; className?: string; tone?: "brand" | "primary" | "warning" | "danger" | "white" }) => (
  <div className={cn("relative isolate h-1.5 min-w-0 transform-gpu overflow-hidden rounded-full bg-surface", className)} role="progressbar" aria-valuenow={Math.round(value * 100)} aria-valuemin={0} aria-valuemax={100}>
    <motion.div className={cn("absolute inset-0 rounded-full will-change-transform", { brand: "bg-brand-gradient", primary: "bg-primary", warning: "bg-warning", danger: "bg-danger", white: "bg-white" }[tone])}
      initial={{ x: "-100%" }} animate={{ x: `${(Math.max(0, Math.min(1, value)) - 1) * 100}%` }} transition={spring.bar} />
  </div>
);
