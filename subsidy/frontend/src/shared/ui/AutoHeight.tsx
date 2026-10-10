import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { spring } from "@/shared/config/motion";

/**
 * Плавная высота под содержимое (ADR-048): измеряет внутренний блок и анимирует height пружиной spring.glide.
 * В отличие от layout-анимации не растягивает текст во время перехода.
 */
export const AutoHeight = ({ children, className }: { children: ReactNode; className?: string }) => {
  const inner = useRef<HTMLDivElement>(null);
  const [h, setH] = useState<number | "auto">("auto");
  useLayoutEffect(() => {
    const el = inner.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setH(el.offsetHeight));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return (
    <motion.div initial={false} animate={{ height: h }} transition={spring.glide} className={cn("overflow-hidden", className)}>
      <div ref={inner}>{children}</div>
    </motion.div>
  );
};
