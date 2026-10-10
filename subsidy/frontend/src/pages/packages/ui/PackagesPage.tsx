import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useInfiniteQuery, useQueries } from "@tanstack/react-query";
import { motion } from "motion/react";
import { AlertTriangle, ChevronRight, FolderOpen, FolderPlus, Search, XCircle } from "lucide-react";
import { packageApi, packageKeys, PackageStatusBadge, ProtocolStatusBadge, type QueueFilter } from "@/entities/package";
import { can, useSession } from "@/entities/session";
import { CreatePackageDialog } from "@/features/create-package";
import { AnimatedNumber, Button, Card, EmptyState, Input, LoadMore, PageHeader, RowsSkeleton, Select, Status } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { routes } from "@/shared/const/router";
import { popIn, stagger, tween } from "@/shared/config/motion";
import { agoRu, cn } from "@/shared/lib";

const POLL_MS = 10_000;
const STATUSES = [
  { value: "", label: "Все пакеты", sub: "в очереди" },
  { value: "COLLECTING", label: "Собираются", sub: "ждут документов" },
  { value: "PROCESSING", label: "Разбираются", sub: "распознавание и проверка" },
  { value: "CHECKED", label: "Проверены", sub: "ждут решения" },
  { value: "DECIDED", label: "С решением", sub: "решение принято" },
];

/** Плитки-счётчики по статусам: число берётся из page.total сервера, клик — фильтр очереди. */
const Counters = ({ inn, status, onStatus }: { inn: string; status: string; onStatus: (v: string) => void }) => {
  const counts = useQueries({
    queries: STATUSES.map((s) => ({
      queryKey: [...packageKeys.queue({ status: s.value, inn }), "count"],
      queryFn: () => packageApi.queue({ status: s.value, inn }, null, 1),
      refetchInterval: POLL_MS,
    })),
  });
  return (
    <motion.dl variants={stagger(0.05)} initial="hidden" animate="show" className="mb-4 grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-5">
      {STATUSES.map((s, i) => {
        const accent = i === 0;
        const active = status === s.value;
        const total = counts[i]?.data?.page?.total;
        return (
          <motion.div key={s.value} variants={popIn} className={cn("min-w-0", accent && "col-span-2 lg:col-span-1")}>
            <button type="button" onClick={() => onStatus(s.value)} aria-pressed={active}
              className={cn("relative flex size-full min-w-0 flex-col gap-1 overflow-hidden rounded-xl px-4 py-4 text-left shadow-card outline-none transition duration-fast hover:-translate-y-0.5 hover:shadow-float focus-visible:ring-2 focus-visible:ring-ring sm:px-5",
                accent ? "bg-brand-deep text-white" : "bg-card", active && !accent && "ring-2 ring-primary", active && accent && "ring-2 ring-white/60")}>
              {accent && <span aria-hidden className="absolute inset-0 bg-sheen" />}
              <dt className={cn("relative truncate text-sm", accent ? "text-white/85" : "text-muted-foreground")}>{s.label}</dt>
              <dd className="relative font-display text-4xl font-semibold tabular-nums tracking-hero">{total === undefined ? <span className="opacity-40">—</span> : <AnimatedNumber value={total} />}</dd>
              <dd className={cn("relative truncate text-xs", accent ? "text-white/80" : "text-muted-foreground")}>{s.sub}</dd>
            </button>
          </motion.div>
        );
      })}
    </motion.dl>
  );
};

/** FE-SP-02: очередь пакетов — фильтры в URL, курсоры, сомнительные видны сразу; обновление раз в 10 с. */
export const PackagesPage = () => {
  const [sp, setSp] = useSearchParams();
  const { roles } = useSession();
  const f: QueueFilter = { status: sp.get("status") ?? "", inn: sp.get("inn") ?? "" };
  const [text, setText] = useState(f.inn ?? "");
  const set = (k: string, v: string) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set(k, v); else n.delete(k); return n; }, { replace: true });
  useEffect(() => { const t = setTimeout(() => { if (text !== (f.inn ?? "")) set("inn", text.trim()); }, 300); return () => clearTimeout(t); });
  const q = useInfiniteQuery({
    queryKey: packageKeys.queue(f),
    queryFn: ({ pageParam }) => packageApi.queue(f, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.page?.next ?? null,
    refetchInterval: POLL_MS,
  });
  const items = q.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const total = q.data?.pages[0]?.page?.total ?? 0;
  const work = can(roles, "work");
  const filtered = !!(f.status || f.inn);

  return (
    <>
      <PageHeader title="Пакеты" sub="Пакеты документов организаций на субсидию: чего не хватает и где ошибки — сразу в списке" />
      {work && <CreatePackageDialog />}
      <Counters inn={f.inn ?? ""} status={f.status ?? ""} onStatus={(v) => set("status", v)} />
      <div className="mb-3 grid gap-2 sm:mb-4 sm:grid-cols-2 lg:grid-cols-4">
        <Input icon={<Search />} value={text} inputMode="numeric" onChange={(e) => setText(e.target.value.replace(/\D/g, ""))} placeholder="ИНН организации" className="bg-card" aria-label="ИНН" />
        <Select aria-label="Статус" className="bg-card" value={f.status ?? ""} options={STATUSES} onChange={(v) => set("status", v)} />
      </div>
      <Card className="overflow-hidden">
        {q.isPending ? <RowsSkeleton rows={8} /> : q.isError ? (
          <EmptyState title="Пакеты не загрузились" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
        ) : !items.length ? (
          <EmptyState icon={<FolderOpen />} title={filtered ? "Ничего не нашли" : "Пакетов пока нет"} text={filtered ? "Измените ИНН или статус" : "Создайте пакет и загрузите документы организации"} className="py-16"
            action={filtered ? <Button variant="secondary" onClick={() => { setText(""); setSp({}, { replace: true }); }}>Сбросить фильтры</Button>
              : work ? <Button onClick={() => set("new", "1")}><FolderPlus />Новый пакет</Button> : undefined} />
        ) : (
          <ul className={cn("transition-opacity duration-fast", q.isFetching && !q.isFetchingNextPage && "opacity-90")}>
            {items.map((it) => {
              const done = it.status !== "COLLECTING" && it.status !== "PROCESSING";
              return (
                <motion.li key={it.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={tween.fast} className="border-t border-border first:border-t-0">
                  <Link to={routes.pkg(it.id)} className="group flex min-w-0 items-center gap-3 px-4 py-3 outline-none transition-colors duration-fast hover:bg-muted focus-visible:bg-muted sm:gap-4 sm:px-6">
                    <span className={cn("grid size-10 shrink-0 place-items-center rounded-md", it.failed ? "bg-danger-soft text-danger-soft-foreground" : it.warnings || it.needsReview ? "bg-warning-soft text-warning-soft-foreground" : "bg-muted text-muted-foreground group-hover:bg-card")}>
                      <FolderOpen className="size-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium">{it.org.name || `ИНН ${it.org.inn}`}</div>
                      <div className="truncate text-xs text-muted-foreground">{it.org.name ? `ИНН ${it.org.inn} · ` : ""}{it.program} · обновлён {agoRu(Date.parse(it.updatedAt))}</div>
                      <div className="mt-1.5 flex flex-wrap items-center gap-1.5 md:hidden">
                        <PackageStatusBadge status={it.status} />
                        {!!it.failed && <Status tone="danger">ошибок: {it.failed}</Status>}
                        {!!it.warnings && <Status tone="warning">предупр.: {it.warnings}</Status>}
                      </div>
                    </div>
                    <div className="hidden shrink-0 flex-wrap items-center justify-end gap-1.5 md:flex md:max-w-md">
                      <PackageStatusBadge status={it.status} />
                      {done && <ProtocolStatusBadge status={it.protocolStatus} />}
                      {!!it.failed && <Status tone="danger"><XCircle />ошибок: {it.failed}</Status>}
                      {!!it.warnings && <Status tone="warning"><AlertTriangle />предупреждений: {it.warnings}</Status>}
                      {!!it.needsReview && <Status tone="info">вручную: {it.needsReview}</Status>}
                    </div>
                    {it.completeness !== null && (
                      <span className="hidden w-16 shrink-0 text-right lg:block">
                        <span className="block text-sm font-medium tabular-nums">{it.completeness}%</span>
                        <span className="block text-xs text-muted-foreground">комплект</span>
                      </span>
                    )}
                    <ChevronRight className="size-4 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
                  </Link>
                </motion.li>
              );
            })}
          </ul>
        )}
        {!q.isPending && !q.isError && items.length > 0 && <LoadMore shown={items.length} total={total} hasMore={!!q.hasNextPage} loading={q.isFetchingNextPage} error={q.isFetchNextPageError} onMore={() => q.fetchNextPage()} />}
      </Card>
    </>
  );
};
