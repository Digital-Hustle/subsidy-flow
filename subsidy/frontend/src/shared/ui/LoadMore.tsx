import { useEffect, useRef } from "react";
import { motion } from "motion/react";
import { tween } from "@/shared/config/motion";
import { cn, plural } from "@/shared/lib";
import { Button } from "./Button";

/** Подвал бесконечного списка: сам подгружает следующую страницу у края прокрутки, кнопка — запасной путь и для клавиатуры. */
export const LoadMore = ({ shown, total, hasMore, loading, error, onMore, className }: { shown: number; total: number; hasMore: boolean; loading: boolean; error: boolean; onMore: () => void; className?: string }) => {
  const ref = useRef<HTMLDivElement>(null);
  const cb = useRef(onMore);
  useEffect(() => { cb.current = onMore; });
  useEffect(() => {
    const el = ref.current;
    if (!el || !hasMore || error || loading) return;
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) cb.current(); }, { rootMargin: "400px 0px" });
    io.observe(el);
    return () => io.disconnect();
  }, [hasMore, error, loading, shown]);
  if (total === 0 && !error) return null;
  return (
    <div ref={ref} className={cn("flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted-foreground sm:px-6", className)} aria-live="polite">
      <span className="tabular-nums">{error ? "Не удалось загрузить данные" : `Показано ${shown} из ${total} ${plural(total, "записи", "записей", "записей")}`}</span>
      {error ? <Button size="sm" variant="secondary" onClick={onMore}>Повторить</Button>
        : hasMore ? <Button size="sm" variant="quiet" disabled={loading} onClick={onMore}>{loading ? "Загружаем…" : "Показать ещё"}</Button>
        : <span>Это все записи</span>}
    </div>
  );
};

/** Заглушка строк списка, пока приходит первая страница. */
export const RowsSkeleton = ({ rows = 6, className }: { rows?: number; className?: string }) => (
  <motion.div className={cn("divide-y divide-border", className)} aria-busy="true" aria-label="Загрузка" initial={{ opacity: 0.55 }} animate={{ opacity: [0.55, 1] }} transition={{ ...tween.base, repeat: Infinity, repeatType: "reverse" }}>
    {Array.from({ length: rows }, (_, i) => (
      <div key={i} className="flex items-center gap-3 px-4 py-3 sm:px-6">
        <div className="size-9 shrink-0 rounded-full bg-muted" />
        <div className="flex min-w-0 flex-1 flex-col gap-2"><div className="h-3 w-1/3 rounded-full bg-muted" /><div className="h-2.5 w-1/4 rounded-full bg-muted" /></div>
      </div>
    ))}
  </motion.div>
);
