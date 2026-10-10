import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock } from "lucide-react";
import { dateRuFull, reviewApi, reviewKeys } from "@/entities/application";
import { typeTitle } from "@/entities/document";
import { Button, Card, EmptyState, PageHeader, RowsSkeleton, Segmented, Status } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { routes } from "@/shared/const/router";

const tone = (d?: number) => ((d ?? 0) < 0 ? "danger" : (d ?? 0) <= 14 ? "warning" : "neutral") as "danger" | "warning" | "neutral";
const left = (d?: number) => ((d ?? 0) < 0 ? "истёк" : `${d ?? 0} дн`);

/** Документы, срок действия которых истекает (сертификаты, декларации) — чтобы предупредить организацию заранее. */
export const ExpiringPage = () => {
  const [days, setDays] = useState("30");
  const q = useQuery({ queryKey: reviewKeys.expiring(Number(days)), queryFn: () => reviewApi.expiring(Number(days)) });
  const items = q.data?.items ?? [];
  return (
    <>
      <PageHeader title="Истекающие документы" sub="Сертификаты и декларации, срок которых заканчивается, — чтобы запросить новые до решения"
        actions={<Segmented label="Период" value={days} onChange={setDays} options={["14", "30", "60", "90"].map((d) => ({ value: d, label: `${d} дн` }))} />} />
      {q.isPending ? <Card className="overflow-hidden"><RowsSkeleton rows={6} /></Card> : q.isError ? (
        <EmptyState title="Отчёт не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<CalendarClock />} title="Ничего не истекает" text={`В ближайшие ${days} дней сроки документов не заканчиваются`} /></Card>
      ) : (
        <>
        <Card className="overflow-hidden md:hidden">
          <ul>
            {items.map((it) => (
              <li key={it.documentId} className="border-t border-border first:border-t-0">
                <Link to={routes.operatorApplication(it.packageId)} className="flex items-center gap-3 px-4 py-3 transition-colors duration-fast hover:bg-muted">
                  <span className="min-w-0 flex-1"><span className="block truncate font-medium">{it.title ?? typeTitle(it.type)}</span><span className="block truncate text-xs text-muted-foreground">ИНН {it.inn ?? "—"} · до {dateRuFull(it.expiresAt)}</span></span>
                  <Status tone={tone(it.daysLeft)}>{left(it.daysLeft)}</Status>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
        <Card className="hidden overflow-x-auto md:block">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted-foreground"><tr className="border-b border-border">
              <th className="p-3 font-medium">Документ</th><th className="p-3 font-medium">Организация</th><th className="p-3 font-medium">Истекает</th><th className="p-3 font-medium">Осталось</th><th className="p-3 font-medium">Заявка</th>
            </tr></thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.documentId} className="border-b border-border last:border-0 hover:bg-muted">
                  <td className="p-3 font-medium">{it.title ?? typeTitle(it.type)}</td>
                  <td className="p-3">{it.inn ? <Link to={routes.operatorOrganization(it.inn)} className="hover:underline">ИНН {it.inn}</Link> : "—"}</td>
                  <td className="p-3 tabular-nums">{dateRuFull(it.expiresAt)}</td>
                  <td className="p-3"><Status tone={tone(it.daysLeft)}>{left(it.daysLeft)}</Status></td>
                  <td className="p-3"><Link to={routes.operatorApplication(it.packageId)} className="text-primary hover:underline">№ {it.packageId.slice(0, 8)}</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
        </>
      )}
    </>
  );
};
