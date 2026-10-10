import { AnimatePresence, motion, useDragControls, type PanInfo } from "motion/react";
import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { cn } from "@/shared/lib";
import { useMediaQuery } from "@/shared/hooks";
import { distance, scale, spring, swipe, tween } from "@/shared/config/motion";

/** Модалка на десктопе, нижняя шторка с перетаскиванием на телефоне. Блокирует прокрутку фона. */
/**
 * Для диалога, который монтируется по условию ({x && <XDialog onClose=…/>}): закрытие сначала проигрывает выход,
 * и только потом родитель размонтирует диалог (onClosed). Без этого модалка исчезает рывком (ADR-048).
 */
export const useDialogState = () => {
  const [open, setOpen] = useState(true);
  return { open, close: useCallback(() => setOpen(false), []) };
};

export const Dialog = ({ open, onClose, onClosed, title, description, children, footer, className }: {
  open: boolean; onClose: () => void; onClosed?: () => void; title?: string; description?: React.ReactNode; children: React.ReactNode; footer?: React.ReactNode; className?: string;
}) => {
  const desktop = useMediaQuery("(min-width: 640px)");
  const drag = useDragControls();
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { removeEventListener("keydown", onKey); document.body.style.overflow = prev; };
  }, [open, onClose]);
  const onDragEnd = (_: unknown, i: PanInfo) => { if (i.offset.y > swipe.offset || i.velocity.y > swipe.velocity) onClose(); };

  return createPortal(
    <AnimatePresence onExitComplete={onClosed}>
      {open && (
        <div className="fixed inset-0 z-modal flex items-end justify-center sm:items-center sm:p-6">
          <motion.div className="absolute inset-0 bg-overlay backdrop-blur-xs" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={tween.base} onClick={onClose} />
          <motion.div role="dialog" aria-modal aria-label={title}
            initial={desktop ? { opacity: 0, scale: scale.popIn, y: distance.enter } : { y: "100%" }}
            animate={desktop ? { opacity: 1, scale: 1, y: 0 } : { y: 0 }}
            exit={desktop ? { opacity: 0, scale: scale.popOut, y: distance.exit, transition: tween.exit } : { y: "100%", transition: spring.sheet }}
            transition={{ ...spring.sheet, opacity: tween.fast }}
            drag={desktop ? false : "y"} dragControls={drag} dragListener={false} dragConstraints={{ top: 0, bottom: 0 }} dragElastic={{ top: 0, bottom: 0.6 }} onDragEnd={onDragEnd}
            className={cn("relative flex max-h-sheet w-full min-w-0 flex-col rounded-t-xl bg-popover text-popover-foreground shadow-pop sm:max-w-lg sm:rounded-xl", className)}>
            {!desktop && (
              <div className="flex shrink-0 cursor-grab touch-none justify-center pb-1 pt-3 active:cursor-grabbing" onPointerDown={(e) => drag.start(e)}>
                <span className="h-1 w-10 rounded-full bg-border-strong" />
              </div>
            )}
            <div className="flex shrink-0 items-start gap-4 px-5 pb-2 pt-2 sm:px-6 sm:pt-6" onPointerDown={(e) => !desktop && drag.start(e)}>
              <div className="min-w-0 flex-1">
                {title && <h2 className="font-display text-xl font-semibold tracking-display">{title}</h2>}
                {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
              </div>
              <button type="button" onClick={onClose} aria-label="Закрыть" className="-mr-2 -mt-1 flex size-control-md shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors duration-fast hover:bg-surface hover:text-foreground"><X className="size-5" /></button>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-6 pt-2 sm:px-6">{children}</div>
            {footer && <div className="flex shrink-0 flex-col-reverse gap-2 border-t border-border px-5 py-4 sm:flex-row sm:justify-end sm:px-6">{footer}</div>}
            <div className="shrink-0 pb-safe" />
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
};
