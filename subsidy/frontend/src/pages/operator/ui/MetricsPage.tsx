import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { BarChart3, Info } from "lucide-react";
import { protocolApi, protocolKeys, ruleTitle } from "@/entities/protocol";
import { AnimatedNumber, Button, Card, CardHeader, CardTitle, DateRangePicker, EmptyState, CardSkeleton, PageHeader, Progress, Select, TilesSkeleton, type DateRange } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { cn, todayKey } from "@/shared/lib";
import { fadeUp, popIn, stagger } from "@/shared/config/motion";

const DAY_MS = 86_400_000;
const daysAgo = (n: number) => todayKey(new Date(Date.now() - n * DAY_MS));

/** Частые ошибки: какие правила чаще всего не проходят заявки за период. Доли и метод расчёта — с сервера. */
export const MetricsPage = () => {
  const [range, setRange] = useState<DateRange>({ from: daysAgo(30), to: todayKey() });
  const [program, setProgram] = useState("");
  const programs = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs });
  const f = { from: range.from, to: range.to, program: program || undefined };
  const q = useQuery({ queryKey: protocolKeys.stats(f), queryFn: () => protocolApi.stats(f) });
  const options = [{ value: "", label: "Все программы" }, ...(programs.data ?? []).map((p) => ({ value: p.code, label: p.name ?? p.code }))];
  const presets = [{ label: "7 дней", range: { from: daysAgo(7), to: todayKey() } }, { label: "30 дней", range: { from: daysAgo(30), to: todayKey() } }, { label: "90 дней", range: { from: daysAgo(90), to: todayKey() } }];
  const items = q.data?.items ?? [];
  const top = items[0];

  return (
    <>
      <PageHeader title="Частые ошибки" sub="Какие проверки чаще всего не проходят заявки — чтобы заранее подсказывать организациям" />
      <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:flex lg:items-center">
        <DateRangePicker aria-label="Период" value={range} onChange={setRange} max={todayKey()} presets={presets} />
        <Select aria-label="Программа" className="bg-card lg:w-72" value={program} options={options} onChange={setProgram} />
      </div>
      {q.isPending ? <div aria-busy="true"><TilesSkeleton /><CardSkeleton lines={6} className="mt-4" /></div> : q.isError ? (
        <EmptyState title="Статистика не загрузилась" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : (
        <>
          <motion.dl variants={stagger(0.06)} initial="hidden" animate="show" className="grid gap-3 sm:gap-4 md:grid-cols-3">
            {[
              { label: "Заявок с протоколом", value: <AnimatedNumber value={q.data.packages ?? 0} />, sub: "за выбранный период" },
              { label: "Правил с нарушениями", value: <AnimatedNumber value={items.length} />, sub: "хотя бы один раз" },
              { label: "Чаще всего", value: top ? ruleTitle(top.rule) : "—", sub: top ? `${top.count} раз · ${(Number(top.share) * 100).toFixed(1)}% заявок` : "нарушений нет", small: true },
            ].map((k, i) => (
              <motion.div key={k.label} variants={popIn} className={cn("relative flex min-w-0 flex-col gap-1 overflow-hidden rounded-xl px-4 py-4 shadow-card sm:px-6 sm:py-5", i === 0 ? "bg-brand-deep text-white" : "bg-card")}>
                {i === 0 && <span aria-hidden className="absolute inset-0 bg-sheen" />}
                <dt className={cn("relative truncate text-sm", i === 0 ? "text-white/85" : "text-muted-foreground")}>{k.label}</dt>
                <dd className={cn("relative font-display font-semibold tabular-nums tracking-hero", k.small ? "line-clamp-2 text-xl sm:text-2xl" : "text-4xl sm:text-5xl")}>{k.value}</dd>
                <dd className={cn("relative truncate text-xs", i === 0 ? "text-white/80" : "text-muted-foreground")}>{k.sub}</dd>
              </motion.div>
            ))}
          </motion.dl>
          <motion.div variants={fadeUp} initial="hidden" animate="show" className="mt-3 sm:mt-4">
            <Card className="overflow-hidden">
              <CardHeader><CardTitle>Правила по частоте нарушений</CardTitle></CardHeader>
              {!items.length ? <EmptyState icon={<BarChart3 />} title="Ошибок нет" text="За период ни одно правило не было нарушено" /> : (
                <ol className="mt-2">
                  {items.map((s, i) => {
                    const share = Number(s.share);
                    return (
                      <li key={s.rule} className="flex items-center gap-3 border-t border-border px-4 py-3 transition-colors duration-fast first:border-t-0 hover:bg-muted sm:gap-4 sm:px-6">
                        <span className="grid size-8 shrink-0 place-items-center rounded-md bg-muted text-sm font-medium tabular-nums text-muted-foreground">{i + 1}</span>
                        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                          <div className="flex items-baseline gap-2 text-sm"><span className="min-w-0 flex-1 truncate font-medium">{ruleTitle(s.rule)} <span className="text-xs font-normal text-muted-foreground">{s.rule}</span></span><span className="shrink-0 tabular-nums text-muted-foreground">{s.count} · {(share * 100).toFixed(1)}%</span></div>
                          <Progress value={share} tone="danger" />
                        </div>
                      </li>
                    );
                  })}
                </ol>
              )}
            </Card>
          </motion.div>
          {q.data.method && (
            <Card className="mt-3 sm:mt-4">
              <CardHeader><CardTitle className="flex items-center gap-2"><Info className="size-4" />Как посчитано</CardTitle></CardHeader>
              <p className="px-4 pb-5 pt-4 text-sm sm:px-6">{q.data.method}</p>
            </Card>
          )}
        </>
      )}
    </>
  );
};
