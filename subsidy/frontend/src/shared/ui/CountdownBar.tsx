import { useEffect } from "react";
import { useAnimate } from "motion/react";
import { cn } from "@/shared/lib";
import { ease, spring } from "@/shared/config/motion";

/**
 * Полоса отсчёта до endsAt. Одна линейная анимация на весь интервал (а не перезапуск пружины каждые 250 мс),
 * полоса сдвигается translateX внутри дорожки — без scaleX, который в iOS Safari оставляет следы у скруглённого края.
 * Дорожка — свой слой (isolate + transform-gpu), чтобы WebKit обрезал полосу по радиусу без артефактов.
 */
export const CountdownBar = ({ endsAt, total, className, fillClassName }: { endsAt: number; total: number; className?: string; fillClassName?: string }) => {
  const [scope, run] = useAnimate<HTMLDivElement>();
  useEffect(() => {
    if (!scope.current) return;
    let alive = true;
    const left = () => Math.max(0, endsAt - Date.now());
    const at = (ms: number) => `${-(1 - Math.min(1, ms / total)) * 100}%`;
    const refill = run(scope.current, { x: at(left()) }, spring.bar);
    refill.then(() => { if (alive && scope.current) run(scope.current, { x: "-100%" }, { duration: left() / 1000, ease: ease.linear }); });
    return () => { alive = false; refill.stop(); };
  }, [endsAt, total, run, scope]);
  return (
    <div className={cn("relative isolate h-1.5 min-w-0 transform-gpu overflow-hidden rounded-full bg-surface", className)} role="timer" aria-label="Время до смены кода">
      <div ref={scope} className={cn("absolute inset-0 rounded-full will-change-transform", fillClassName ?? "bg-brand-gradient")} />
    </div>
  );
};
