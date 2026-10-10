// Индикатор активного элемента дорожки (сегменты, навигация, таб-бары).
// Левый и правый края — независимые пружины с отскоком; каждый край зажат в границах дорожки.
// Поэтому у крайних пунктов индикатор не вылетает наружу, а «сжимается» об стенку и возвращается.
import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { motion, useMotionValue, useSpring, useTransform } from "motion/react";
import { spring } from "@/shared/config/motion";
import { cn } from "@/shared/lib";

const ACTIVE = '[aria-current="page"],[aria-selected="true"],[aria-checked="true"],[data-active="true"]';

/** ref вешается на контейнер с position: relative, пункты которого помечены aria-current / aria-selected / data-active. */
export function useTrackIndicator(dep: unknown) {
  const ref = useRef<HTMLDivElement>(null);
  const left = useSpring(0, spring.snappy);
  const right = useSpring(0, spring.snappy);
  const lo = useMotionValue(0), hi = useMotionValue(0);
  const [visible, setVisible] = useState(false);
  const placed = useRef(false);

  const measure = useCallback((instant: boolean) => {
    const el = ref.current;
    const items = el ? [...el.children].filter((c): c is HTMLElement => c instanceof HTMLElement && !c.hasAttribute("data-indicator")) : [];
    const a = el?.querySelector<HTMLElement>(ACTIVE);
    if (!el || !a || !items.length) { setVisible(false); placed.current = false; return; }
    lo.set(items[0].offsetLeft);
    hi.set(items[items.length - 1].offsetLeft + items[items.length - 1].offsetWidth);
    const L = a.offsetLeft, R = a.offsetLeft + a.offsetWidth;
    if (instant || !placed.current) { left.jump(L); right.jump(R); } else { left.set(L); right.set(R); }
    placed.current = true;
    setVisible(true);
  }, [left, right, lo, hi]);

  useLayoutEffect(() => { measure(false); }, [dep, measure]);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(() => measure(true));
    ro.observe(el);
    return () => ro.disconnect();
  }, [measure]);

  const x = useTransform(() => Math.min(Math.max(left.get(), lo.get()), hi.get()));
  const width = useTransform(() => Math.max(0, Math.min(Math.max(right.get(), lo.get()), hi.get()) - x.get()));
  return { ref, x, width, visible };
}

export const TrackIndicator = ({ ind, className, children }: { ind: ReturnType<typeof useTrackIndicator>; className?: string; children?: React.ReactNode }) => (
  <motion.span data-indicator aria-hidden style={{ x: ind.x, width: ind.width }}
    className={cn("pointer-events-none absolute left-0 transition-opacity duration-fast", ind.visible ? "opacity-100" : "opacity-0", className)}>{children}</motion.span>
);
