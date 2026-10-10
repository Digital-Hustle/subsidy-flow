import { Monitor, Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { Segmented } from "./Segmented";

type Mode = "light" | "dark" | "system";
const THEME_STORE = "subsidy.theme";
const read = (): Mode => (localStorage.getItem(THEME_STORE) as Mode) || "system";
const resolve = (m: Mode) => m === "dark" || (m === "system" && matchMedia("(prefers-color-scheme: dark)").matches);

/** Применить сохранённую тему (дублирует no-flash скрипт в index.html — на случай SPA-переходов). */
export const applyStoredTheme = () => document.documentElement.classList.toggle("dark", resolve(read()));

/** Смена темы плавным переливом (View Transitions), если тема действительно меняется. */
const applyAnimated = () => {
  const changes = document.documentElement.classList.contains("dark") !== resolve(read());
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (changes && !reduce && document.startViewTransition) document.startViewTransition(applyStoredTheme);
  else applyStoredTheme();
};

/** Режим темы с сохранением и подпиской на системную. */
export const useThemeMode = () => {
  const [mode, setMode] = useState<Mode>(read);
  useEffect(() => {
    localStorage.setItem(THEME_STORE, mode);
    applyAnimated();
    if (mode !== "system") return;
    const mq = matchMedia("(prefers-color-scheme: dark)");
    mq.addEventListener("change", applyAnimated);
    return () => mq.removeEventListener("change", applyAnimated);
  }, [mode]);
  return [mode, setMode] as const;
};

export type ThemeMode = Mode;

export const ThemeSwitcher = ({ className }: { className?: string }) => {
  const [mode, setMode] = useThemeMode();
  return (
    <Segmented size="sm" value={mode} onChange={setMode} label="Тема" className={className}
      options={[
        { value: "light", label: <span className="sr-only">Светлая</span>, icon: <Sun />, title: "Светлая" },
        { value: "dark", label: <span className="sr-only">Тёмная</span>, icon: <Moon />, title: "Тёмная" },
        { value: "system", label: <span className="sr-only">Как в системе</span>, icon: <Monitor />, title: "Как в системе" },
      ]} />
  );
};
