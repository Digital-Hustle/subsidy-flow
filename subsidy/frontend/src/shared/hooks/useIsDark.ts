import { useEffect, useState } from "react";

/** Текущая тема по классу .dark на <html> (меняют ThemeSwitcher и киоск). */
export const useIsDark = () => {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"));
  useEffect(() => {
    const html = document.documentElement;
    setDark(html.classList.contains("dark")); // класс мог смениться между рендером и эффектом (уход с киоска)
    const mo = new MutationObserver(() => setDark(html.classList.contains("dark")));
    mo.observe(html, { attributes: true, attributeFilter: ["class"] });
    return () => mo.disconnect();
  }, []);
  return dark;
};
