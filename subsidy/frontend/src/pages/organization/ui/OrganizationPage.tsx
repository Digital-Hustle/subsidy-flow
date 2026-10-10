import { Link, useParams } from "react-router";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { ArrowLeft, ChevronRight, FolderOpen, History } from "lucide-react";
import { packageApi, packageKeys, PackageStatusBadge } from "@/entities/package";
import { HISTORY_ACTION, protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, EmptyState, PageHeader, RowsSkeleton } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { agoRu } from "@/shared/lib";

const detailsText = (d: Record<string, unknown> | null) =>
  d ? Object.entries(d).filter(([, v]) => v !== null && v !== "").map(([k, v]) => `${k}: ${typeof v === "object" ? JSON.stringify(v) : String(v)}`).join(" · ") : "";

/** FE-SP-07: организация — её пакеты и лента событий (создание, протоколы, правки, решения). */
export const OrganizationPage = () => {
  const { inn = "" } = useParams();
  const history = useQuery({ queryKey: protocolKeys.history(inn), queryFn: () => protocolApi.history(inn) });
  const f = { inn };
  const packages = useInfiniteQuery({
    queryKey: packageKeys.queue(f), queryFn: ({ pageParam }) => packageApi.queue(f, pageParam),
    initialPageParam: null as string | null, getNextPageParam: (last) => last.page?.next ?? null,
  });
  const pk = packages.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const name = pk.find((p) => p.org.name)?.org.name;

  return (
    <>
      <Link to={routes.packages} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Пакеты</Link>
      <PageHeader title={name ?? `ИНН ${inn}`} sub={name ? `ИНН ${inn} · пакеты и история проверок` : "Пакеты и история проверок организации"} />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="min-w-0 self-start overflow-hidden">
          <CardHeader><CardTitle>Пакеты · {pk.length}</CardTitle></CardHeader>
          {packages.isPending ? <CardContent><RowsSkeleton rows={3} /></CardContent> : !pk.length ? <CardContent><p className="text-sm text-muted-foreground">Пакетов нет</p></CardContent> : (
            <ul className="mt-2">
              {pk.map((p) => (
                <li key={p.id} className="border-t border-border first:border-t-0">
                  <Link to={routes.pkg(p.id)} className="group flex min-w-0 items-center gap-3 px-4 py-3 text-sm outline-none transition-colors duration-fast hover:bg-muted focus-visible:bg-muted sm:px-6">
                    <span className="grid size-9 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground group-hover:bg-card"><FolderOpen className="size-4" /></span>
                    <span className="min-w-0 flex-1"><span className="block truncate font-medium">{p.program}</span><span className="block truncate text-xs text-muted-foreground">{agoRu(Date.parse(p.updatedAt))}</span></span>
                    <PackageStatusBadge status={p.status} />
                    <ChevronRight className="size-4 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card className="min-w-0 lg:col-span-2">
          <CardHeader><CardTitle>История</CardTitle></CardHeader>
          <CardContent>
            {history.isPending ? <RowsSkeleton rows={5} /> : history.isError ? <EmptyState title="История не загрузилась" text={errorText(history.error)} action={<Button variant="secondary" onClick={() => history.refetch()}>Повторить</Button>} />
              : !history.data.items.length ? <EmptyState icon={<History />} title="Событий нет" /> : (
                <ol className="relative flex flex-col gap-4 pl-6 before:absolute before:inset-y-1 before:left-1.5 before:w-px before:bg-border">
                  {history.data.items.map((h, i) => (
                    <li key={i} className="relative flex min-w-0 flex-col gap-0.5">
                      <span aria-hidden className={`absolute -left-6 top-1.5 size-3 rounded-full ring-4 ring-card ${i === 0 ? "bg-primary" : "bg-border-strong"}`} />
                      <div className="text-sm font-medium">{HISTORY_ACTION[h.action] ?? h.action}</div>
                      <div className="text-xs text-muted-foreground">{new Date(h.at).toLocaleString("ru-RU")} · {agoRu(Date.parse(h.at))}{h.by ? ` · ${h.by}` : ""}
                        {h.packageId && <> · <Link className="underline-offset-2 hover:underline" to={routes.pkg(h.packageId)}>пакет</Link></>}</div>
                      {detailsText(h.details) && <div className="break-words text-xs text-muted-foreground">{detailsText(h.details)}</div>}
                    </li>
                  ))}
                </ol>
              )}
          </CardContent>
        </Card>
      </div>
    </>
  );
};
