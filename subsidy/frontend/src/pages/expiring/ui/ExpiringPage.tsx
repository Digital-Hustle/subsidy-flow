import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CalendarClock, ChevronRight } from "lucide-react";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Card, EmptyState, PageHeader, RowsSkeleton, Segmented, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn, plural } from "@/shared/lib";
import { tween } from "@/shared/config/motion";

const RANGES = [{ value: "7", label: "7 дней" }, { value: "14", label: "14 дней" }, { value: "30", label: "30 дней" }];
const URGENT_DAYS = 3;

/** FE-SP-09: справки и выписки, у которых истекает срок действия — срок считает rules. */
export const ExpiringPage = () => {
  const [days, setDays] = useState("14");
  const q = useQuery({ queryKey: protocolKeys.expiring(Number(days)), queryFn: () => protocolApi.expiring(Number(days)) });
  const items = q.data?.items ?? [];
  return (
    <>
      <PageHeader title="Истекающие документы" sub="Справки и выписки, которые скоро перестанут действовать — запросите новые заранее"
        actions={<Segmented label="Горизонт" value={days} onChange={setDays} options={RANGES} />} />
      {q.isPending ? <RowsSkeleton rows={6} /> : q.isError ? (
        <EmptyState title="Список не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<CalendarClock />} title="Ничего не истекает" text={`В ближайшие ${days} дней сроки не заканчиваются`} className="py-16" /></Card>
      ) : (
        <Card className="overflow-hidden">
          <ul>
            {items.map((it) => {
              const exp = new Date(it.expiresAt);
              const urgent = it.daysLeft <= URGENT_DAYS;
              return (
                <motion.li key={it.documentId} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={tween.fast} className="border-t border-border first:border-t-0">
                  <Link to={`${routes.pkg(it.packageId)}?tab=documents&doc=${it.documentId}`} className="group flex min-w-0 items-center gap-3 px-4 py-3 outline-none transition-colors duration-fast hover:bg-muted focus-visible:bg-muted sm:gap-4 sm:px-6">
                    <span className={cn("flex size-12 shrink-0 flex-col items-center justify-center rounded-md leading-none", urgent ? "bg-danger-soft text-danger-soft-foreground" : "bg-warning-soft text-warning-soft-foreground")}>
                      <span className="font-display text-lg font-semibold tabular-nums">{exp.getDate()}</span>
                      <span className="text-xs">{exp.toLocaleDateString("ru-RU", { month: "short" }).replace(".", "")}</span>
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium">{it.title}</div>
                      <div className="truncate text-xs text-muted-foreground">ИНН {it.inn} · от {new Date(it.date).toLocaleDateString("ru-RU")} · до {exp.toLocaleDateString("ru-RU")}</div>
                    </div>
                    <Status tone={urgent ? "danger" : "warning"} className="hidden sm:inline-flex">
                      {it.daysLeft <= 0 ? "истёк" : `осталось ${it.daysLeft} ${plural(it.daysLeft, "день", "дня", "дней")}`}
                    </Status>
                    <ChevronRight className="size-4 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
                  </Link>
                </motion.li>
              );
            })}
          </ul>
        </Card>
      )}
    </>
  );
};
