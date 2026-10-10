import { AnimatePresence, motion, type HTMLMotionProps } from "motion/react";
import { Check } from "lucide-react";
import { cn } from "@/shared/lib";
import { press, spring, tween } from "@/shared/config/motion";

/**
 * Чип выбора (ADR-048): плоская плашка без рамки. Выбранный — тинт accent и галочка, которая
 * выезжает пружиной и раздвигает текст (ширина анимируется, соседние чипы не прыгают рывком).
 */
export const Chip = ({ pressed, className, children, ...p }: Omit<HTMLMotionProps<"button">, "children"> & { pressed?: boolean; children?: React.ReactNode }) => (
  <motion.button type="button" layout="position" {...press} aria-pressed={pressed} {...p}
    className={cn("flex h-control-sm items-center rounded-sm px-3 text-sm font-medium outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring",
      pressed ? "bg-accent text-accent-foreground" : "bg-surface text-muted-foreground hover:bg-surface-hover hover:text-foreground", className)}>
    <AnimatePresence initial={false}>
      {pressed && (
        <motion.span key="on" initial={{ width: 0, opacity: 0 }} animate={{ width: "auto", opacity: 1 }} exit={{ width: 0, opacity: 0 }} transition={{ ...spring.snappy, opacity: tween.fast }} className="flex overflow-hidden">
          <Check className="mr-1.5 size-4 shrink-0" />
        </motion.span>
      )}
    </AnimatePresence>
    {children}
  </motion.button>
);
