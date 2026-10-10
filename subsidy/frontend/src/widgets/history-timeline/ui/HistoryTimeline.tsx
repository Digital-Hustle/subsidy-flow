import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { History } from "lucide-react";
import { actionLabel, dateTimeRu, decisionView, fieldLabel, reviewApi, reviewKeys, sourceLabel, type HistoryEntry } from "@/entities/application";
import { orgActionLabel, protocolApi } from "@/entities/protocol";
import { Button, EmptyState, RowsSkeleton } from "@/shared/ui";
import { fadeUp, stagger } from "@/shared/config/motion";

const detailsText = (d: unknown): string | null => {
  if (!d || typeof d !== "object") return null;
  const o = d as Record<string, unknown>;
  const parts: string[] = [];
  if (o.field) parts.push(fieldLabel(String(o.field)));
  if ("from" in o || "to" in o) parts.push(`${o.from ?? "—"} → ${o.to ?? "—"}`);
  else if ("value" in o) parts.push(`→ ${String(o.value ?? "—")}`);
  if (o.decision) parts.push(decisionView(String(o.decision)).label.toLowerCase());
  if (o.status && !o.decision) parts.push(String(o.status));
  if (o.version) parts.push(`версия ${String(o.version)}`);
  if (o.name && typeof o.name === "string") parts.push(o.name);
  if (o.reason && typeof o.reason === "string") parts.push(`«${o.reason}»`);
  if (o.comment) parts.push(`«${String(o.comment)}»`);
  if (o.format) parts.push(String(o.format).toUpperCase());
  if (Array.isArray(o.items)) parts.push(o.items.map((i: { reason?: string }) => `«${i.reason ?? ""}»`).join(", "));
  return parts.length ? parts.join(" · ") : null;
};

/**
 * История заявки — кто, что и когда. Основной источник — package /packages/{id}/history; пока его нет на стенде,
 * берём ленту организации из rules и оставляем события этого пакета.
 */
const load = async (packageId: string, inn?: string): Promise<HistoryEntry[]> => {
  const r = await reviewApi.history(packageId);
  if (r.items) return r.items;
  if (!inn) return [];
  const org = await protocolApi.orgHistory(inn);
  return org.filter((e) => !e.packageId || e.packageId === packageId)
    .map((e, i) => ({ id: `${e.at}-${i}`, action: e.action, actor: e.by, details: e.details, createdAt: e.at, source: e.by ? "SPECIALIST" : "SYSTEM" }))
    .map((e) => ({ ...e, action: actionLabel(e.action) === e.action ? orgActionLabel(e.action) : e.action }));
};

export const HistoryTimeline = ({ packageId, inn }: { packageId: string; inn?: string }) => {
  const q = useQuery({ queryKey: [...reviewKeys.history(packageId), inn], queryFn: () => load(packageId, inn) });
  if (q.isPending) return <RowsSkeleton rows={4} />;
  if (q.isError) return <EmptyState title="История не загрузилась" action={<Button size="sm" variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;
  const items = [...(q.data ?? [])].sort((a, b) => Date.parse(b.createdAt ?? "") - Date.parse(a.createdAt ?? ""));
  if (!items.length) return <EmptyState icon={<History />} title="Событий пока нет" />;
  return (
    <motion.ol variants={stagger(0.02)} initial="hidden" animate="show" className="relative ml-2 flex flex-col gap-3 border-l border-border pl-4">
      {items.map((h) => (
        <motion.li key={h.id} variants={fadeUp} className="relative">
          <span className="absolute -left-5 top-1.5 size-2 rounded-full bg-primary" />
          <div className="text-sm font-medium">{actionLabel(h.action)}</div>
          <div className="text-xs text-muted-foreground">{dateTimeRu(h.createdAt)} · {sourceLabel(h.source)}{h.actor ? ` ${h.actor}` : ""}</div>
          {detailsText(h.details) && <div className="mt-0.5 text-xs text-muted-foreground">{detailsText(h.details)}</div>}
        </motion.li>
      ))}
    </motion.ol>
  );
};
