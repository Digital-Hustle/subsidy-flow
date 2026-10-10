import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { keepPreviousData, useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, ChevronRight, Inbox, Loader2, Search, UserRound } from "lucide-react";
import { AppStatusBadge, dateTimeRu, reviewApi, reviewKeys, statusView, waitingRu, type QueueFilter } from "@/entities/application";
import { can, useSession } from "@/entities/session";
import { CreateApplicationButton } from "@/features/manage-application";
import { TakeNextButton, useTakeNext } from "@/features/take-next";
import { useProgramName } from "@/entities/protocol";
import { can, useSession } from "@/entities/session";
import { AnimatedNumber, Avatar, Button, Card, CardHeader, CardTitle, EmptyState, Input, LoadMore, PageHeader, RowsSkeleton, Segmented, Select, Status } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";
import { fadeUp, spring, stagger } from "@/shared/config/motion";

const STATUSES = [
  { value: "", label: "Все" },
  { value: "CHECKED", label: "Проверены, ждут решения" },
  { value: "PROCESSING", label: "Разбираются" },
  { value: "SUBMITTED", label: "Отправлены" },
  { value: "NEEDS_FIX", label: "Ждём исправлений" },
  { value: "COLLECTING", label: "Собираются" },
  { value: "DECIDED", label: "С решением" },
];

const ASIDE = ["CHECKED", "PROCESSING", "NEEDS_FIX", "COLLECTING", "DECIDED"] as const;

const AsideCount = ({ status }: { status: string }) => {
  const q = useQuery({ queryKey: reviewKeys.count(status), queryFn: () => reviewApi.count(status), refetchInterval: 30_000 });
  return <>{q.isPending ? "…" : q.isError ? "—" : <AnimatedNumber value={q.data} />}</>;
};

/** Боковая сводка очереди на широком экране: счётчики по статусам (клик — фильтр), горячие клавиши. */
const QueueAside = ({ status, onStatus }: { status: string; onStatus: (v: string) => void }) => {
  return (
    <aside className="hidden w-80 shrink-0 flex-col gap-4 xl:sticky xl:top-24 xl:flex">
      <Card className="overflow-hidden">
        <CardHeader><CardTitle>Сводка</CardTitle></CardHeader>
        <ul className="flex flex-col p-2">
          {ASIDE.map((k) => (
            <li key={k}>
              <button type="button" onClick={() => onStatus(status === k ? "" : k)} aria-pressed={status === k}
                className={cn("relative flex w-full items-center justify-between gap-3 rounded-md px-3 py-2.5 text-left text-sm outline-none transition-colors duration-fast hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-ring", status === k && "font-medium")}>
                {status === k && <motion.span layoutId="queue-aside-active" transition={spring.snappy} className="absolute inset-0 rounded-md bg-muted" />}
                <span className="relative truncate">{statusView(k).label}</span>
                <span className="relative font-semibold tabular-nums"><AsideCount status={k} /></span>
              </button>
            </li>
          ))}
        </ul>
      </Card>
      <Card className="p-4 sm:p-5">
        <h2 className="mb-3 font-display text-base font-semibold">Клавиши</h2>
        <dl className="flex flex-col gap-2 text-sm">
          {[["N", "следующая заявка"], ["J / K", "поле вниз / вверх"], ["E", "править поле"]].map(([k, v]) => (
            <div key={k} className="flex items-center gap-3"><dt className="w-14 shrink-0"><kbd className="rounded-xs bg-surface px-1.5 py-0.5 font-mono text-xs">{k}</kbd></dt><dd className="text-muted-foreground">{v}</dd></div>
          ))}
        </dl>
      </Card>
    </aside>
  );
};

/** Очередь заявок — фильтры в URL, курсоры, «взять следующую», обновление без перезагрузки. */
export const QueuePage = () => {
  const programName = useProgramName();
  const [sp, setSp] = useSearchParams();
  const next = useTakeNext();
  const session = useSession();
  const text0 = sp.get("q") ?? "";
  const inn = /^\d{10}(\d{2})?$/.test(text0) ? text0 : "";
  const f: QueueFilter = { status: sp.get("status") ?? "", q: text0, inn, assignee: sp.get("assignee") ?? "", sort: sp.get("sort") ?? "waiting" };
  const [text, setText] = useState(f.q ?? "");
  const set = (k: string, v: string) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set(k, v); else n.delete(k); return n; }, { replace: true });
  useEffect(() => { const t = setTimeout(() => { if (text !== (f.q ?? "")) set("q", text.trim()); }, 300); return () => clearTimeout(t); });
  const q = useInfiniteQuery({
    queryKey: reviewKeys.queue(f),
    queryFn: ({ pageParam }) => reviewApi.queue(f, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.page?.next ?? null,
    placeholderData: keepPreviousData, // смена фильтра — без мигания скелетоном, строки перестраиваются
    refetchInterval: 15_000, // у package поток событий только по пакету (/stream?packageId=) — очередь опрашиваем
  });
  useEffect(() => {
    const on = (e: KeyboardEvent) => { if (e.key === "n" && !(e.target instanceof HTMLElement && e.target.closest("input, textarea, [role=dialog], [role=combobox]")) && !next.isPending) next.mutate(); };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [next]);
  const items = q.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const total = q.data?.pages[0]?.page?.total ?? 0;

  return (
    <>
      <PageHeader title="Очередь заявок" sub="Сначала — дольше всех ждущие. Ошибки и предупреждения протокола видны сразу" actions={<div className="flex flex-wrap gap-2">{can(session.roles, "act") && <CreateApplicationButton />}<TakeNextButton /></div>} />
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:gap-6">
        <div className="min-w-0 flex-1">
      <div className="mb-4 flex flex-col gap-2 md:flex-row md:flex-wrap md:items-center">
        <Input icon={<Search />} value={text} onChange={(e) => setText(e.target.value)} placeholder="Организация, ИНН или номер заявки" className="bg-card md:min-w-64 md:flex-1" aria-label="Поиск" />
        <Select aria-label="Статус" className="bg-card md:w-56" value={f.status ?? ""} options={STATUSES} onChange={(v) => set("status", v)} />
        <div className="flex flex-wrap gap-2">
          <Segmented label="Чьи" value={f.assignee ? "me" : "all"} onChange={(v) => set("assignee", v === "me" ? "me" : "")} options={[{ value: "all", label: "Все" }, { value: "me", label: "Мои" }]} />
          <Segmented label="Сортировка" value={f.sort === "updated" ? "updated" : "waiting"} onChange={(v) => set("sort", v === "waiting" ? "" : v)}
            options={[{ value: "waiting", label: "Дольше ждут" }, { value: "updated", label: "Недавние" }]} />
        </div>
      </div>
      {q.isPending ? <Card className="overflow-hidden"><RowsSkeleton rows={8} /></Card> : q.isError ? (
        <EmptyState title="Очередь не загрузилась" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<Inbox />} title="Заявок нет" text={f.status || f.q || f.assignee ? "Под фильтр ничего не попало" : "Как только заявитель отправит комплект, он появится здесь"} /></Card>
      ) : (
        <>
          <Card className="overflow-hidden">
            <motion.ul variants={stagger(0.02)} initial="hidden" animate="show" className={q.isPlaceholderData ? "opacity-70 transition-opacity duration-fast" : "transition-opacity duration-fast"}>
              <AnimatePresence mode="popLayout" initial={false}>
              {items.map((it) => (
                <motion.li key={it.id} layout="position" variants={fadeUp} exit="exit" className="border-t border-border first:border-t-0">
                  <Link to={routes.operatorApplication(it.id)} className="group flex min-w-0 items-center gap-3 px-4 py-3.5 outline-none transition-colors duration-fast hover:bg-muted focus-visible:bg-muted sm:gap-4 sm:px-6">
                    <Avatar name={it.org?.name || it.applicantName || "?"} className="hidden size-10 sm:flex" />
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium">{it.org?.name || it.applicantName || (it.org?.inn ? `ИНН ${it.org.inn}` : "Организация")}</div>
                      <div className="truncate text-xs text-muted-foreground">№ {it.id.slice(0, 8)}{it.org?.inn ? ` · ИНН ${it.org.inn}` : ""}{it.program ? ` · ${programName(it.program)}` : ""} · {it.waitingSeconds != null ? `ждёт ${waitingRu(it.waitingSeconds)}` : `обновлена ${dateTimeRu(it.updatedAt)}`}</div>
                    </div>
                    <div className="hidden flex-wrap items-center justify-end gap-1.5 md:flex">
                      {it.completeness != null && <Status tone={it.completeness >= 100 ? "success" : "neutral"}>комплект {it.completeness}%</Status>}
                      {!!it.failed && <Status tone="danger"><AlertTriangle />ошибок: {it.failed}</Status>}
                      {!!it.warnings && <Status tone="warning">предупр.: {it.warnings}</Status>}
                      {!!it.needsReview && <Status tone="info">вручную: {it.needsReview}</Status>}
                      {!!it.processing && <Status tone="info"><Loader2 />разбирается: {it.processing}</Status>}
                      {!!it.missing?.length && <Status tone="danger">не хватает: {it.missing.length}</Status>}
                      {it.assignee && <Status><UserRound />{it.assignee}</Status>}
                    </div>
                    <AppStatusBadge status={it.status} />
                    <ChevronRight className="size-4 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
                  </Link>
                </motion.li>
              ))}
              </AnimatePresence>
            </motion.ul>
          </Card>
          <LoadMore className="mt-4" shown={items.length} total={total} hasMore={!!q.hasNextPage} loading={q.isFetchingNextPage} error={q.isFetchNextPageError} onMore={() => q.fetchNextPage()} />
        </>
      )}
        </div>
        <QueueAside status={f.status ?? ""} onStatus={(v) => set("status", v)} />
      </div>
    </>
  );
};
