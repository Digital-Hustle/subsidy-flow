import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

// Наши размеры текста (text-xs…6xl) — это font-size, а не цвет: объясняем это tailwind-merge.
const twMerge = extendTailwindMerge({
  extend: { classGroups: { "font-size": [{ text: ["xs", "sm", "base", "lg", "xl", "2xl", "3xl", "4xl", "5xl", "6xl"] }] } },
});

export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs));
