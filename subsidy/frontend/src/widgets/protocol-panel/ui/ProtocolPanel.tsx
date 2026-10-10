import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, ClipboardCopy, FileSearch, Hourglass } from "lucide-react";
import { fieldLabel } from "@/entities/application";
import { typeTitle } from "@/entities/document";
import { CHECKLIST_STATUS, ProtocolStatusBadge, isProblem, protocolApi, protocolKeys, ruleView, type Protocol, type RuleResult } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { Button, Card, EmptyState, Progress, RowsSkeleton, Segmented, Select, Status, toast } from "@/shared/ui";
import { agoRu } from "@/shared/lib";

export type EvidenceJump = (documentId: string, page: number | null, field: string | null) => void;

const Completeness = ({ p }: { p: Protocol }) => {
  const c = p.completeness ?? {};
  const items = c.items ?? [];
  const copy = () => { if (c.request) void navigator.clipboard.writeText(c.request).then(() => toast.success("Текст запроса скопирован")); };
  return (
    <Card className="p-4">
      <div className="flex items-baseline justify-between gap-3"><h3 className="font-display text-base font-semibold">Комплект документов</h3><span className="text-sm tabular-nums text-muted-foreground">{c.percent ?? 0}%</span></div>
      <Progress value={(c.percent ?? 0) / 100} className="mt-3" />
      {items.length > 0 && (
        <ul className="mt-3 flex flex-col gap-1">
          {items.map((it) => {
            const v = CHECKLIST_STATUS[it.status ?? ""] ?? { label: it.status ?? "—", tone: "neutral" as const };
            return (
              <li key={it.item} className="-mx-2 flex items-start gap-2 rounded-md px-2 py-1 text-sm transition-colors duration-fast hover:bg-muted">
                <span className="min-w-0 flex-1">{it.title ?? it.item}{(it.conditionText ?? it.condition) && <span className="text-muted-foreground"> · {it.conditionText ?? it.condition}</span>}</span>
                <Status tone={v.tone}>{v.label}</Status>
              </li>
            );
          })}
        </ul>
      )}
      {c.request && (
        <div className="mt-3 flex flex-col gap-2 rounded-md bg-surface p-3">
          <div className="text-sm font-medium">Текст запроса заявителю</div>
          <p className="whitespace-pre-wrap text-sm">{c.request}</p>
          <Button size="sm" variant="secondary" className="self-start" onClick={copy}><ClipboardCopy />Скопировать</Button>
        </div>
      )}
    </Card>
  );
};

const Result = ({ r, onEvidence }: { r: RuleResult; onEvidence: EvidenceJump }) => {
  const v = ruleView(r.status);
  return (
    <li className="flex flex-col gap-1 py-3">
      <div className="flex flex-wrap items-center gap-2"><Status tone={v.tone}>{v.label}</Status><span className="text-xs text-muted-foreground">{r.rule}</span></div>
      {r.message && <p className="text-sm">{r.message}</p>}
      {(r.expected || r.actual) && (
        <div className="flex flex-col text-sm">
          {r.expected && <div><span className="text-muted-foreground">ожидалось: </span><span className="tabular-nums">{r.expected}</span></div>}
          {r.actual && <div><span className="text-muted-foreground">в документах: </span><span className="tabular-nums">{r.actual}</span></div>}
        </div>
      )}
      {!!r.evidence?.length && (
        <div className="flex flex-wrap gap-1">
          {r.evidence.filter((e) => e.documentId).map((e, i) => (
            <Button key={i} size="sm" variant="quiet" onClick={() => onEvidence(e.documentId!, e.page ?? null, e.field ?? null)}><FileSearch />{e.field ? fieldLabel(e.field) : "документ"}{e.page ? `, стр. ${e.page}` : ""}</Button>
          ))}
        </div>
      )}
    </li>
  );
};

/** Протокол проверки: полнота комплекта, результаты правил с ожидалось/в документах и переходом к документу, версии. */
export const ProtocolPanel = ({ packageId, onEvidence }: { packageId: string; onEvidence: EvidenceJump }) => {
  const [version, setVersion] = useState("");
  const [group, setGroup] = useState<"problems" | "all">("problems");
  const versions = useQuery({ queryKey: protocolKeys.versions(packageId), queryFn: () => protocolApi.versions(packageId), retry: false });
  const latest = useQuery({ queryKey: protocolKeys.latest(packageId), queryFn: () => protocolApi.latest(packageId), enabled: !version, retry: false });
  const chosen = useQuery({ queryKey: protocolKeys.version(packageId, Number(version)), queryFn: () => protocolApi.version(packageId, Number(version)), enabled: !!version });
  const q = version ? chosen : latest;

  if (q.isPending) return <Card className="overflow-hidden"><RowsSkeleton rows={4} /></Card>;
  if (q.isError) {
    if (q.error instanceof ApiError && (q.error.code === "NOT_CHECKED_YET" || q.error.status === 404 || q.error.status === 409)) {
      return <Card><EmptyState icon={<Hourglass />} title="Проверка ещё идёт" text="Протокол появится, когда все документы будут разобраны" className="py-10" /></Card>;
    }
    return <EmptyState title="Протокол не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;
  }
  const p = q.data;
  const results = [...(p.results ?? [])].sort((a, b) => ruleView(a.status).order - ruleView(b.status).order);
  const shown = group === "all" ? results : results.filter((r) => isProblem(r.status));
  const options = [{ value: "", label: "Последняя версия" }, ...(versions.data ?? []).map((v) => ({ value: String(v.version), label: `Версия ${v.version}`, hint: v.createdAt ? agoRu(Date.parse(v.createdAt)) : undefined }))];
  const missing = p.completeness?.missing ?? [];

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <ProtocolStatusBadge status={p.status} />
        <span className="text-xs text-muted-foreground">версия {p.version}{p.rulesVersion ? ` · правила ${p.rulesVersion}` : ""}{p.createdAt ? ` · ${agoRu(Date.parse(p.createdAt))}` : ""}</span>
        <span className="flex-1" />
        {options.length > 2 && <Select aria-label="Версия протокола" size="sm" className="w-full sm:w-48" value={version} options={options} onChange={setVersion} />}
      </div>
      {missing.length > 0 && (
        <Card className="bg-danger-soft p-4 text-danger-soft-foreground">
          <p className="text-sm font-semibold">Не хватает документов</p>
          <ul className="mt-1 list-disc pl-5 text-sm">{missing.map((m) => <li key={m.type + (m.item ?? "")}>{typeTitle(m.type)}{m.reason ? ` — ${m.reason}` : ""}</li>)}</ul>
        </Card>
      )}
      <Card className="p-4">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="flex-1 font-display text-base font-semibold">Проверки · {results.length}</h3>
          <Segmented size="sm" label="Показать" value={group} onChange={setGroup} options={[{ value: "problems", label: "Проблемы" }, { value: "all", label: "Все" }]} />
        </div>
        {!shown.length ? <EmptyState icon={<CheckCircle2 />} title="Проблем не найдено" text="Все применимые правила пройдены" className="py-8" /> : (
          <ul className="flex flex-col divide-y divide-border">{shown.map((r) => <Result key={r.rule} r={r} onEvidence={onEvidence} />)}</ul>
        )}
      </Card>
      <Completeness p={p} />
    </div>
  );
};
