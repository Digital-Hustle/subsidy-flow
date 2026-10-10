import { useSyncExternalStore } from "react";

/** Подписка на media query (ширина экрана, prefers-color-scheme и т. п.). */
export const useMediaQuery = (q: string) =>
  useSyncExternalStore(
    (cb) => { const m = matchMedia(q); m.addEventListener("change", cb); return () => m.removeEventListener("change", cb); },
    () => matchMedia(q).matches,
    () => false,
  );
