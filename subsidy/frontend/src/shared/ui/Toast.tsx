import { AnimatePresence, motion } from "motion/react";
import { create } from "zustand";
import { CheckCircle2, AlertTriangle, Info } from "lucide-react";
import { spring, tween } from "@/shared/config/motion";
import { motion as m } from "@/shared/config/tokens";
import { cn } from "@/shared/lib";

type Toast = { id: number; text: string; tone: "success" | "error" | "info" };
const useToasts = create<{ list: Toast[]; push: (t: Omit<Toast, "id">) => void; drop: (id: number) => void }>((set) => ({
  list: [],
  push: (t) => {
    const id = Date.now() + Math.random();
    set((s) => ({ list: [...s.list.slice(-2), { ...t, id }] }));
    setTimeout(() => set((s) => ({ list: s.list.filter((x) => x.id !== id) })), t.tone === "error" ? m.toast.errorDurationMs : m.toast.durationMs);
  },
  drop: (id) => set((s) => ({ list: s.list.filter((x) => x.id !== id) })),
}));

export const toast = {
  success: (text: string) => useToasts.getState().push({ text, tone: "success" }),
  error: (text: string) => useToasts.getState().push({ text, tone: "error" }),
  info: (text: string) => useToasts.getState().push({ text, tone: "info" }),
};

const ICON = { success: CheckCircle2, error: AlertTriangle, info: Info };
const TONE = { success: "text-success", error: "text-danger", info: "text-info" };

export const Toaster = () => {
  const { list, drop } = useToasts();
  return (
    <div className="pointer-events-none fixed inset-x-0 top-0 z-toast flex flex-col items-center gap-2 px-4 pt-safe">
      <div className="h-3" />
      <AnimatePresence>
        {list.map((t) => {
          const Icon = ICON[t.tone];
          return (
            <motion.button key={t.id} type="button" layout onClick={() => drop(t.id)}
              initial={{ y: -32, opacity: 0, scale: 0.9 }} animate={{ y: 0, opacity: 1, scale: 1 }} exit={{ y: -16, opacity: 0, scale: 0.96, transition: tween.exit }}
              transition={{ ...spring.pop, opacity: tween.fast }}
              className="pointer-events-auto flex min-h-control-md max-w-full items-center gap-3 rounded-full bg-inverse py-2.5 pl-4 pr-5 text-left text-sm font-medium text-inverse-foreground shadow-pop sm:max-w-md">
              <Icon className={cn("size-5 shrink-0", TONE[t.tone])} />
              <span className="min-w-0">{t.text}</span>
            </motion.button>
          );
        })}
      </AnimatePresence>
    </div>
  );
};
