import { AnimatePresence, motion } from "motion/react";
import { WifiOff } from "lucide-react";
import { useOnline } from "@/shared/hooks";
import { spring, tween } from "@/shared/config/motion";

export const OfflineBanner = ({ text = "Нет связи — показываем сохранённые данные" }: { text?: string }) => {
  const online = useOnline();
  return (
    <AnimatePresence>
      {!online && (
        <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ ...spring.soft, opacity: tween.fast }}
          className="overflow-hidden">
          <div className="mx-auto my-2 flex w-fit max-w-full items-center gap-2 rounded-full border border-warning-border bg-warning-soft px-4 py-2 text-sm font-medium text-warning-soft-foreground"><WifiOff className="size-4 shrink-0" />{text}</div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};
