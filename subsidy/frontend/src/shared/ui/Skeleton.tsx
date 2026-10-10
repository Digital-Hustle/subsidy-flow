import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { duration, ease } from "@/shared/config/motion";

const shimmer = { repeat: Infinity, duration: duration.loop / 2, ease: ease.inOut, repeatType: "mirror" } as const;

/** Скелетон вместо спиннера: мягкое «дыхание». */
export const Skeleton = ({ className }: { className?: string }) => (
  <motion.div aria-hidden initial={{ opacity: 0.55 }} animate={{ opacity: 1 }} transition={shimmer} className={cn("rounded-md bg-surface", className)} />
);

/** Заголовок страницы-заглушка: строка заголовка и подзаголовка (как PageHeader). */
export const HeaderSkeleton = ({ className }: { className?: string }) => (
  <div aria-hidden className={cn("mb-6 flex flex-col gap-3", className)}><Skeleton className="h-9 w-64 max-w-full rounded-lg sm:h-11" /><Skeleton className="h-4 w-96 max-w-full rounded-full" /></div>
);

/** Плитки KPI-заглушки (как ряд карточек метрик). */
export const TilesSkeleton = ({ count = 3, className }: { count?: number; className?: string }) => (
  <div aria-hidden className={cn("grid grid-cols-2 gap-3 sm:gap-4", count === 4 ? "sm:grid-cols-4" : "md:grid-cols-3", className)}>
    {Array.from({ length: count }, (_, i) => (
      <div key={i} className="flex flex-col gap-3 rounded-xl bg-card px-4 py-4 shadow-card sm:px-6 sm:py-5"><Skeleton className="h-3 w-2/3 rounded-full" /><Skeleton className="h-9 w-1/2 rounded-lg" /></div>
    ))}
  </div>
);

/** Карточка-заглушка с строками текста. */
export const CardSkeleton = ({ lines = 4, className }: { lines?: number; className?: string }) => (
  <div aria-hidden className={cn("flex flex-col gap-3 rounded-xl bg-card p-4 shadow-card sm:p-6", className)}>
    <Skeleton className="h-5 w-40 rounded-full" />
    {Array.from({ length: lines }, (_, i) => <Skeleton key={i} className={cn("h-3 rounded-full", i % 2 ? "w-3/4" : "w-full")} />)}
  </div>
);
