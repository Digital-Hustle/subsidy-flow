import { useEffect, useRef, useState } from "react";
import { motion, useAnimate } from "motion/react";
import { Check } from "lucide-react";
import { cn } from "@/shared/lib";
import { duration, ease, popIn, spring, tween } from "@/shared/config/motion";

export type CodeState = "idle" | "busy" | "error" | "success";

/**
 * Поле одноразового кода (ADR-046): ячейки по цифре поверх одного настоящего input — работают вставка,
 * автоподстановка кода из SMS/письма (autocomplete="one-time-code") и стирание. Когда введены все цифры —
 * onComplete (автоотправка). Заполнение: цифра выпрыгивает, ячейка заливается; проверка — волна по ячейкам;
 * ошибка — ячейки отскакивают пружиной и очищаются; успех — заливка фирменным цветом и галочка.
 */
export const CodeInput = ({ length = 6, value, onChange, onComplete, state = "idle", autoFocus, label = "Код", className }: {
  length?: number; value: string; onChange: (v: string) => void; onComplete?: (v: string) => void; state?: CodeState; autoFocus?: boolean; label?: string; className?: string;
}) => {
  const input = useRef<HTMLInputElement>(null);
  const [focus, setFocus] = useState(false);
  const [scope, animate] = useAnimate<HTMLDivElement>();
  const locked = state === "busy" || state === "success";

  useEffect(() => {
    if (state !== "error" || !scope.current) return;
    animate(scope.current, { x: [18, 0] }, { ...spring.pop, velocity: -600 });
  }, [state, animate, scope]);
  useEffect(() => { if (state === "error" || state === "idle") input.current?.focus(); }, [state]);

  const set = (raw: string) => {
    const v = raw.replace(/\D/g, "").slice(0, length);
    onChange(v);
    if (v.length === length && v !== value) onComplete?.(v);
  };
  const active = Math.min(value.length, length - 1);

  return (
    <div ref={scope} className={cn("relative", className)}>
      <input ref={input} value={value} onChange={(e) => set(e.target.value)} disabled={locked} autoFocus={autoFocus}
        inputMode="numeric" autoComplete="one-time-code" maxLength={length} aria-label={label} aria-invalid={state === "error"}
        onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
        className="absolute inset-0 z-raised w-full cursor-text bg-transparent text-transparent caret-transparent outline-none selection:bg-transparent" />
      <div className="grid gap-2" style={{ gridTemplateColumns: `repeat(${length}, minmax(0, 1fr))` }} aria-hidden>
        {Array.from({ length }, (_, i) => {
          const ch = value[i];
          const isActive = focus && !locked && i === active && (value.length < length || i === length - 1);
          return (
            <motion.div key={i} className={cn("relative flex aspect-4/5 max-h-16 items-center justify-center overflow-hidden rounded-lg bg-card font-display text-2xl font-semibold tabular-nums ring-1 transition-colors duration-fast sm:text-3xl",
                state === "error" ? "ring-danger text-danger" : isActive ? "ring-2 ring-ring" : ch ? "ring-border-strong" : "ring-border",
                state === "success" && "text-white ring-transparent")}
              animate={state === "busy" ? { y: [0, -6] } : { y: 0 }}
              transition={state === "busy" ? { duration: duration.slow, ease: ease.inOut, repeat: Infinity, repeatType: "reverse", delay: i * 0.07 } : spring.soft}>
              {/* Заливка ячейки: вырастает из центра, когда цифра введена; на успехе — фирменная. */}
              <motion.span aria-hidden className={cn("absolute inset-0", state === "success" ? "bg-brand-deep" : "bg-accent")}
                initial={false} animate={{ scale: ch || state === "success" ? 1 : 0.4, opacity: ch || state === "success" ? 1 : 0 }}
                transition={{ ...spring.pop, delay: state === "success" ? i * 0.05 : 0, opacity: tween.fast }} />
              {state === "success" && i === length - 1 ? (
                <motion.span variants={popIn} initial="hidden" animate="show" transition={{ ...spring.pop, delay: length * 0.05 }} className="relative"><Check className="size-6" strokeWidth={3} /></motion.span>
              ) : ch ? (
                <motion.span key={ch + i} className="relative" initial={{ scale: 0.3, y: 10, opacity: 0 }} animate={{ scale: 1, y: 0, opacity: 1 }} transition={{ ...spring.pop, opacity: tween.fast }}>{ch}</motion.span>
              ) : isActive ? (
                <motion.span className="relative h-7 w-0.5 rounded-full bg-foreground" animate={{ opacity: [1, 0] }} transition={{ duration: duration.slow * 1.2, ease: ease.inOut, repeat: Infinity, repeatType: "reverse" }} />
              ) : null}
            </motion.div>
          );
        })}
      </div>
    </div>
  );
};
