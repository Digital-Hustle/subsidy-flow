import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { spring } from "@/shared/config/motion";

export const Switch = ({ checked, onChange, label, id }: { checked: boolean; onChange: (v: boolean) => void; label?: string; id?: string }) => (
  <button id={id} type="button" role="switch" aria-checked={checked} aria-label={label} onClick={() => onChange(!checked)}
    className={cn("relative flex h-7 w-12 shrink-0 items-center rounded-full p-0.5 outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background", checked ? "justify-end bg-primary" : "justify-start bg-border-strong")}>
    <motion.span layout transition={spring.snappy} whileTap={{ width: 28 }} className="h-6 w-6 rounded-full bg-white shadow-float" />
  </button>
);

/** Строка настройки: текст слева, свитч справа; вся строка — цель касания. */
export const SwitchRow = ({ title, text, icon, checked, onChange }: { title: string; text?: string; icon?: React.ReactNode; checked: boolean; onChange: (v: boolean) => void }) => (
  <label className="flex min-h-control-lg cursor-pointer items-center gap-3 rounded-md px-1 py-2">
    {icon && <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface text-muted-foreground [&_svg]:size-4.5">{icon}</span>}
    <span className="min-w-0 flex-1">
      <span className="block text-sm font-medium">{title}</span>
      {text && <span className="block text-xs text-muted-foreground">{text}</span>}
    </span>
    <Switch checked={checked} onChange={onChange} label={title} />
  </label>
);
