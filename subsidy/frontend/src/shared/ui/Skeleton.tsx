import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { duration, ease } from "@/shared/config/motion";

const shimmer = { repeat: Infinity, duration: duration.loop / 2, ease: ease.inOut, repeatType: "mirror" } as const;

/** Скелетон вместо спиннера: мягкое «дыхание». */
export const Skeleton = ({ className }: { className?: string }) => (
  <motion.div aria-hidden initial={{ opacity: 0.55 }} animate={{ opacity: 1 }} transition={shimmer} className={cn("rounded-md bg-surface", className)} />
);
