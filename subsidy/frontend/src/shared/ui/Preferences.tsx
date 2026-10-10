import { useState } from "react";
import { motion } from "motion/react";
import { Check, Monitor, Moon, Palette, Sun } from "lucide-react";
import { cn } from "@/shared/lib";
import { press, duration } from "@/shared/config/motion";
import { TrackIndicator, useTrackIndicator } from "./TrackIndicator";
import { Dialog } from "./Dialog";
import { useThemeMode, type ThemeMode } from "./ThemeSwitcher";

const MODES: { value: ThemeMode; label: string; icon: typeof Sun }[] = [
  { value: "light", label: "Светлая", icon: Sun },
  { value: "dark", label: "Тёмная", icon: Moon },
  { value: "system", label: "Системная", icon: Monitor },
];

/** Мини-экран темы: .light / .dark принудительно задают переменные, независимо от текущей темы страницы. */
const Mini = ({ theme, className }: { theme: "light" | "dark"; className?: string }) => (
  <span className={cn(theme, "flex items-end bg-page p-2", className)}><span className="h-5 w-3/4 rounded-xs bg-card shadow-xs" /></span>
);

/** Выбор темы карточками-превью. Галочка переезжает между карточками пружиной (TrackIndicator). */
export const ThemePicker = ({ className, onPick }: { className?: string; onPick?: (m: ThemeMode) => void }) => {
  const [mode, setMode] = useThemeMode();
  const ind = useTrackIndicator(mode);
  return (
    <div ref={ind.ref} role="radiogroup" aria-label="Тема" className={cn("relative grid grid-cols-3 gap-2 sm:gap-3", className)}>
      <TrackIndicator ind={ind} className="top-0 z-raised h-full">
        <span className="absolute right-3 top-3 flex size-6 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-xs"><Check className="size-3.5" strokeWidth={3} /></span>
      </TrackIndicator>
      {MODES.map(({ value, label, icon: Icon }) => {
        const on = mode === value;
        return (
          <motion.button key={value} type="button" role="radio" aria-checked={on} {...press} onClick={() => { setMode(value); onPick?.(value); }}
            className={cn("relative flex flex-col gap-2 rounded-lg p-1.5 pb-3 text-left outline-none ring-2 transition-colors duration-base focus-visible:ring-ring", on ? "bg-accent ring-brand" : "bg-muted ring-transparent hover:bg-surface")}>
            <span className="relative flex h-16 overflow-hidden rounded-md ring-1 ring-border">
              {value === "system" ? (<><Mini theme="light" className="w-1/2 pr-0" /><Mini theme="dark" className="w-1/2 pl-0" /></>) : <Mini theme={value} className="w-full" />}
            </span>
            <span className="flex items-center gap-1.5 px-1.5 text-sm font-medium"><Icon className="hidden size-4 shrink-0 text-muted-foreground sm:block" /><span className="truncate">{label}</span></span>
          </motion.button>
        );
      })}
    </div>
  );
};

/** Кнопка «Оформление» для шапки: открывает шторку/модалку с выбором темы и доп. содержимым. */
export const PreferencesButton = ({ className, children }: { className?: string; children?: React.ReactNode }) => {
  const [open, setOpen] = useState(false);
  return (
    <>
      <motion.button type="button" {...press} onClick={() => setOpen(true)} aria-label="Оформление" title="Оформление"
        className={cn("flex size-control-md shrink-0 items-center justify-center rounded-md text-muted-foreground outline-none transition-colors duration-fast hover:bg-surface hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring", className)}>
        <Palette className="size-5" />
      </motion.button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Оформление" description="Настройка сохраняется на этом устройстве. Киоск всегда в тёмной теме.">
        <ThemePicker onPick={() => setTimeout(() => setOpen(false), duration.slow * 1000)} />
        {children}
      </Dialog>
    </>
  );
};
