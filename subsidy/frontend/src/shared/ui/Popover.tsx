import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "@/shared/lib";
import { spring, tween } from "@/shared/config/motion";

type Pos = { left: number; top?: number; bottom?: number; maxH: number };

/** Поповер в портале под якорем: переворот вверх у края экрана, закрытие по клику мимо и Esc. Основа для календаря и выбора времени. */
export const usePopover = (minWidth = 280, prefH = 360) => {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<Pos | null>(null);
  const anchor = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const place = useCallback(() => {
    const r = anchor.current?.getBoundingClientRect();
    if (!r) return;
    const below = innerHeight - r.bottom - 12, above = r.top - 12;
    const left = Math.max(8, Math.min(r.left, innerWidth - minWidth - 8));
    const up = below < prefH && above > below;
    setPos(up ? { left, bottom: innerHeight - r.top + 6, maxH: above } : { left, top: r.bottom + 6, maxH: below });
  }, [minWidth, prefH]);
  useLayoutEffect(() => { if (open) place(); }, [open, place]);
  useEffect(() => {
    if (!open) return;
    const close = (e: Event) => { if (!panel.current?.contains(e.target as Node) && !anchor.current?.contains(e.target as Node)) setOpen(false); };
    const key = (e: KeyboardEvent) => { if (e.key === "Escape") { setOpen(false); anchor.current?.focus(); } };
    addEventListener("pointerdown", close, true);
    addEventListener("keydown", key);
    // Прокрутка внутри самой панели (колонки времени, длинный список) позицию не меняет — не перерисовываем.
    const onScroll = (e: Event) => { if (!panel.current?.contains(e.target as Node)) place(); };
    addEventListener("resize", place);
    addEventListener("scroll", onScroll, true);
    return () => { removeEventListener("pointerdown", close, true); removeEventListener("keydown", key); removeEventListener("resize", place); removeEventListener("scroll", onScroll, true); };
  }, [open, place]);
  return { open, setOpen, pos, anchor, panel };
};

export const PopoverPanel = ({ state, children, className, label }: { state: ReturnType<typeof usePopover>; children: React.ReactNode; className?: string; label: string }) =>
  createPortal(
    <AnimatePresence>
      {state.open && state.pos && (
        <motion.div ref={state.panel} role="dialog" aria-label={label}
          initial={{ opacity: 0, y: state.pos.top !== undefined ? -6 : 6, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, scale: 0.98, transition: tween.exit }}
          transition={{ ...spring.snappy, opacity: tween.fast }}
          className={cn("fixed z-dropdown overflow-y-auto overscroll-contain rounded-lg border border-border bg-popover p-3 text-popover-foreground shadow-pop", state.pos.top !== undefined ? "origin-top-left" : "origin-bottom-left", className)}
          style={{ left: state.pos.left, top: state.pos.top, bottom: state.pos.bottom, maxHeight: state.pos.maxH }}>
          {children}
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
