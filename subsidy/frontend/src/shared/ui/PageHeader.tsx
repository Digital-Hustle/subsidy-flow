import { motion } from "motion/react";
import { fadeUp, stagger } from "@/shared/config/motion";
import { cn } from "@/shared/lib";

/** Заголовок страницы в духе sberbank.ru: крупный Display 500, плотный трекинг, лид приглушённым. */
export const PageHeader = ({ title, sub, actions, kicker, className }: { title: React.ReactNode; sub?: React.ReactNode; actions?: React.ReactNode; kicker?: React.ReactNode; className?: string }) => (
  <motion.div variants={stagger()} initial="hidden" animate="show" className={cn("mb-5 flex flex-col gap-4 sm:mb-8 md:flex-row md:items-end md:justify-between", className)}>
    <div className="min-w-0">
      {kicker && <motion.div variants={fadeUp} className="mb-2 text-sm font-medium text-brand">{kicker}</motion.div>}
      <motion.h1 variants={fadeUp} className="text-balance font-display text-3xl font-semibold tracking-hero sm:text-4xl">{title}</motion.h1>
      {sub && <motion.p variants={fadeUp} className="mt-1.5 max-w-2xl text-pretty text-sm text-muted-foreground sm:text-base">{sub}</motion.p>}
    </div>
    {actions && <motion.div variants={fadeUp} className="flex min-w-0 shrink-0 flex-wrap items-center gap-2">{actions}</motion.div>}
  </motion.div>
);
