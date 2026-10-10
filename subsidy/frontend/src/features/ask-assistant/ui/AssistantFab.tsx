import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Maximize2, Sparkles, X } from "lucide-react";
import { Button, LogoMark } from "@/shared/ui";
import { spring } from "@/shared/config/motion";
import { AssistantChat } from "./AssistantChat";

/** Плавающий помощник на карточке заявки: мини-чат с той же историей, что во вкладке «Помощник». */
export const AssistantFab = ({ packageId, onExpand }: { packageId: string; onExpand?: () => void }) => {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);
  return (
    <>
      <AnimatePresence>
        {!open && (
          <motion.div initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.6 }} transition={spring.snappy} className="fixed bottom-24 right-4 z-overlay lg:bottom-6 lg:right-6">
            <Button variant="brand" size="icon-lg" aria-label="Открыть помощника" onClick={() => setOpen(true)} className="size-14 rounded-full shadow-float"><Sparkles className="size-6" /></Button>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {open && (
          <motion.div role="dialog" aria-label="Помощник" initial={{ opacity: 0, y: 24, scale: 0.96 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 24, scale: 0.96 }} transition={spring.soft}
            className="fixed inset-x-3 bottom-3 top-20 z-modal flex origin-bottom-right flex-col overflow-hidden rounded-xl bg-card text-card-foreground shadow-pop ring-1 ring-border sm:inset-x-auto sm:bottom-6 sm:right-6 sm:top-auto sm:h-chat sm:w-full sm:max-w-md">
            <div className="flex items-center gap-2 border-b border-border px-3 py-2.5">
              <LogoMark className="size-8" />
              <div className="min-w-0 flex-1 truncate text-sm font-semibold">Помощник по заявке</div>
              {onExpand && <Button size="icon-sm" variant="quiet" aria-label="Открыть во вкладке" onClick={() => { setOpen(false); onExpand(); }}><Maximize2 /></Button>}
              <Button size="icon-sm" variant="quiet" aria-label="Закрыть" onClick={() => setOpen(false)}><X /></Button>
            </div>
            <AssistantChat packageId={packageId} compact className="min-h-0 flex-1 rounded-none shadow-none" />
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};
