import { motion, useSpring, useTransform } from "motion/react";
import { useEffect } from "react";
import { spring } from "@/shared/config/motion";

/** Счётчик на пружине с перелётом: число «доезжает» с отскоком, не линейно. */
export const AnimatedNumber = ({ value, format = (n) => String(Math.round(n)), className }: { value: number; format?: (n: number) => string; className?: string }) => {
  const s = useSpring(0, spring.counter);
  const text = useTransform(s, (v) => format(Math.max(0, v)));
  useEffect(() => { s.set(value); }, [s, value]);
  return <motion.span className={className} aria-label={format(value)}>{text}</motion.span>;
};
