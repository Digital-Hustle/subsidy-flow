import { Link, useParams } from "react-router";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowLeft, ChevronRight, FolderOpen, History } from "lucide-react";
import { AppStatusBadge, dateTimeRu, fieldLabel, reviewApi, reviewKeys } from "@/entities/application";
import { orgActionLabel, protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, EmptyState, PageHeader, RowsSkeleton, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { agoRu } from "@/shared/lib";
import { fadeUp, stagger } from "@/shared/config/motion";

const detailsText = (d?: Record<string, unknown> | null) =>
  d ? Object.entries(d).filter(([, v]) => v !== null && v !== "" && typeof v !== "object").map(([k, v]) => `${k === "field" ? "поле" : k}: ${k === "field" ? fieldLabel(String(v)) : String(v)}`).join(" · ") : "";

/** Организация: все её заявки и лента событий (создание, протоколы, правки, решения). */
export const OrganizationPage = () => {
  const { inn = "" } = useParams();
  const history = useQuery({ queryKey: protocolKeys.orgHistory(inn), queryFn: () => protocolApi.orgHistory(inn) });
  const f = { inn };
  const packages = useInfiniteQuery({
    queryKey: reviewKeys.queue(f), queryFn: ({ pageParam }) => reviewApi.queue(f, pageParam),
    initialPageParam: null as string | null, getNextPageParam: (last) => last.page?.next ?? null,
  });
  const pk = packages.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const name = pk.find((p) => p.org?.name)?.org?.name;
  const events = history.data ?? [];

  return (
    <>
      <Link to={routes.operator} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Очередь</Link>
      <PageHeader title={name ?? `ИНН ${inn}`} sub={name ? `ИНН ${inn} · заявки и история проверок` : "Заявки и история проверок организации"} />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="min-w-0 self-start overflow-hidden">
          <CardHeader><CardTitle>Заявки</CardTitle><Status>{pk.length}</Status></CardHeader>
          {packages.isPending ? <RowsSkeleton rows={3} /> : !pk.length ? <CardContent><p className="text-sm text-muted-foreground">Заявок нет</p></CardContent> : (
            <motion.ul variants={stagger(0.03)} initial="hidden" animate="show" className="mt-2">
              {pk.map((p) => (
                <motion.li key={p.id} variants={fadeUp} className="border-t border-border first:border-t-0">
                  <Link to={routes.operatorApplication(p.id)} className="group flex min-w-0 items-center gap-3 px-4 py-3 text-sm outline-none transition-colors duration-fast hover:bg-muted focus-visible:bg-muted sm:px-6">
                    <span className="grid size-9 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground group-hover:bg-card"><FolderOpen className="size-4" /></span>
                    <span className="min-w-0 flex-1"><span className="block truncate font-medium">{p.program ?? `№ ${p.id.slice(0, 8)}`}</span><span className="block truncate text-xs text-muted-foreground">{p.updatedAt ? agoRu(Date.parse(p.updatedAt)) : ""}</span></span>
                    <AppStatusBadge status={p.status} />
                    <ChevronRight className="size-4 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
                  </Link>
                </motion.li>
              ))}
            </motion.ul>
          )}
        </Card>
        <Card className="min-w-0 lg:col-span-2">
          <CardHeader><CardTitle>История</CardTitle></CardHeader>
          <CardContent>
            {history.isPending ? <RowsSkeleton rows={5} /> : history.isError ? <EmptyState title="История не загрузилась" text={errorText(history.error)} action={<Button variant="secondary" onClick={() => history.refetch()}>Повторить</Button>} />
              : !events.length ? <EmptyState icon={<History />} title="Событий нет" /> : (
                <motion.ol variants={stagger(0.02)} initial="hidden" animate="show" className="relative ml-2 flex flex-col gap-3 border-l border-border pl-4">
                  {events.map((h, i) => (
                    <motion.li key={i} variants={fadeUp} className="relative">
                      <span className="absolute -left-5 top-1.5 size-2 rounded-full bg-primary" />
                      <div className="text-sm font-medium">{orgActionLabel(h.action)}</div>
                      <div className="text-xs text-muted-foreground">{dateTimeRu(h.at)}{h.by ? ` · ${h.by}` : ""}
                        {h.packageId && <> · <Link className="underline-offset-2 hover:underline" to={routes.operatorApplication(h.packageId)}>заявка № {h.packageId.slice(0, 8)}</Link></>}</div>
                      {detailsText(h.details) && <div className="mt-0.5 break-words text-xs text-muted-foreground">{detailsText(h.details)}</div>}
                    </motion.li>
                  ))}
                </motion.ol>
              )}
          </CardContent>
        </Card>
      </div>
    </>
  );
};
