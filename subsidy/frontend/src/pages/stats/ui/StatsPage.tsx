import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { BarChart3, Info } from "lucide-react";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { AnimatedNumber, Button, Card, CardHeader, CardTitle, DateRangePicker, EmptyState, PageHeader, Progress, RowsSkeleton, Select, type DateRange } from "@/shared/ui";
import { cn, todayKey } from "@/shared/lib";
import { fadeUp, popIn, stagger } from "@/shared/config/motion";

const PERCENT = 100;
const DAY_MS = 86_400_000;
const daysAgo = (n: number) => todayKey(new Date(Date.now() - n * DAY_MS));

/** FE-SP-08: частые ошибки по правилам за период; доли и метод расчёта приходят с сервера. */
export const StatsPage = () => {
  const [range, setRange] = useState<DateRange>({ from: daysAgo(30), to: todayKey() });
  const [program, setProgram] = useState("");
  const programs = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs });
  const f = { from: range.from, to: range.to, program: program || undefined };
  const q = useQuery({ queryKey: protocolKeys.stats(f), queryFn: () => protocolApi.stats(f) });
  const options = [{ value: "", label: "Все программы" }, ...(programs.data?.items ?? []).map((p) => ({ value: p.code, label: p.name }))];
  const presets = [{ label: "7 дней", range: { from: daysAgo(7), to: todayKey() } }, { label: "30 дней", range: { from: daysAgo(30), to: todayKey() } }, { label: "90 дней", range: { from: daysAgo(90), to: todayKey() } }];

  return (
    <>
      <PageHeader title="Частые ошибки" sub="Какие проверки чаще всего не проходят пакеты — чтобы заранее подсказывать организациям" />
      <div className="mb-3 grid gap-2 sm:mb-4 sm:grid-cols-2 lg:flex lg:items-center">
        <DateRangePicker aria-label="Период" value={range} onChange={setRange} max={todayKey()} presets={presets} />
        <Select aria-label="Программа" className="bg-card lg:w-72" value={program} options={options} onChange={setProgram} />
      </div>
      {q.isPending ? <RowsSkeleton rows={6} /> : q.isError ? (
        <EmptyState title="Статистика не загрузилась" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : (
        <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
          <dl className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
            {[
              { label: "Пакетов с протоколом", value: q.data.packages, sub: "за выбранный период" },
              { label: "Правил с нарушениями", value: q.data.items.length, sub: "хотя бы один раз" },
            ].map((t, i) => (
              <motion.div key={t.label} variants={popIn} className={cn("relative flex min-w-0 flex-col gap-1 overflow-hidden rounded-xl px-4 py-4 shadow-card sm:px-6 sm:py-5", i === 0 ? "bg-brand-deep text-white" : "bg-card")}>
                {i === 0 && <span aria-hidden className="absolute inset-0 bg-sheen" />}
                <dt className={cn("relative truncate text-sm", i === 0 ? "text-white/85" : "text-muted-foreground")}>{t.label}</dt>
                <dd className="relative font-display text-4xl font-semibold tabular-nums tracking-hero sm:text-5xl"><AnimatedNumber value={t.value} /></dd>
                <dd className={cn("relative truncate text-xs", i === 0 ? "text-white/80" : "text-muted-foreground")}>{t.sub}</dd>
              </motion.div>
            ))}
            {q.data.items[0] && (
              <motion.div variants={popIn} className="col-span-2 flex min-w-0 flex-col gap-1 rounded-xl bg-card px-4 py-4 shadow-card sm:px-6 sm:py-5 lg:col-span-1">
                <dt className="truncate text-sm text-muted-foreground">Чаще всего</dt>
                <dd className="truncate font-display text-2xl font-semibold tracking-hero sm:text-3xl">{q.data.items[0].rule}</dd>
                <dd className="truncate text-xs text-muted-foreground">{q.data.items[0].count} раз · {(Number(q.data.items[0].share) * PERCENT).toFixed(1)}% пакетов</dd>
              </motion.div>
            )}
          </dl>
          <motion.div variants={fadeUp}>
            <Card className="overflow-hidden">
              <CardHeader><CardTitle>Правила по частоте нарушений</CardTitle></CardHeader>
              {!q.data.items.length ? <EmptyState icon={<BarChart3 />} title="Ошибок нет" text="За период ни одно правило не было нарушено" /> : (
                <ol className="mt-2">
                  {q.data.items.map((s, i) => {
                    const pct = Number(s.share) * PERCENT;
                    return (
                      <li key={s.rule} className="flex items-center gap-3 border-t border-border px-4 py-3 transition-colors duration-fast first:border-t-0 hover:bg-muted sm:gap-4 sm:px-6">
                        <span className="grid size-8 shrink-0 place-items-center rounded-md bg-muted text-sm font-medium tabular-nums text-muted-foreground">{i + 1}</span>
                        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                          <div className="flex items-baseline gap-2 text-sm"><span className="min-w-0 flex-1 truncate font-medium">{s.rule}</span><span className="shrink-0 tabular-nums text-muted-foreground">{s.count} · {pct.toFixed(1)}%</span></div>
                          <Progress value={pct} tone="danger" />
                        </div>
                      </li>
                    );
                  })}
                </ol>
              )}
              <p className="m-4 flex gap-2 rounded-md bg-muted p-3 text-xs text-muted-foreground sm:mx-6"><Info className="size-4 shrink-0" />Как посчитано: {q.data.method}</p>
            </Card>
          </motion.div>
        </motion.div>
      )}
    </>
  );
};
