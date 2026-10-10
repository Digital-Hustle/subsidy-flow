import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, FileText, History } from "lucide-react";
import { arrangeFiles, neighbourDoc, packageApi, packageKeys, PackageStatusBadge, reasonText, StageBadge, type DocumentResult, type FileCard, type PackageCard } from "@/entities/package";
import { protocolKeys } from "@/entities/protocol";
import { can, useSession } from "@/entities/session";
import { AssistantChat } from "@/features/ask-assistant";
import { DecideButton } from "@/features/decide-package";
import { ExportButtons } from "@/features/export-protocol";
import { RebuildButton } from "@/features/rebuild-projection";
import { FileActions } from "@/features/manage-file";
import { UploadDropzone } from "@/features/upload-files";
import { DocumentViewer } from "@/widgets/document-viewer";
import { ProtocolPanel } from "@/widgets/protocol-panel";
import { errorText, useEventStream } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, EmptyState, PageHeader, RowsSkeleton, Segmented, Select, Status, Switch } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";

type Tab = "protocol" | "documents" | "assistant";
const EVENTS = ["file.stage", "document.parsed", "package.checked"];
const DECISION = { APPROVE: "Одобрено", REJECT: "Отказано", RETURN: "Возвращено на доработку" } as const;
const PERCENT = 100;
const TABS: Tab[] = ["protocol", "documents", "assistant"];
const DIGIT = /^Digit([1-3])$/;

/** Горячие клавиши не перехватывают ввод в полях, селектах и диалогах. */
const typing = (e: KeyboardEvent) => {
  const t = e.target as HTMLElement | null;
  return e.ctrlKey || e.metaKey || e.altKey || !!t?.closest("input, textarea, select, [contenteditable=true], [role=combobox], [role=dialog]");
};

const Files = ({ card, files, docs, typeName, types, type, onType, doubtFirst, onDoubtFirst, selected, onSelect, canEdit, canReprocess }: {
  card: PackageCard; files: FileCard[]; docs: Map<string, DocumentResult>; typeName: (t: string | null) => string;
  types: { value: string; label: string }[]; type: string; onType: (v: string) => void; doubtFirst: boolean; onDoubtFirst: (v: boolean) => void;
  selected: string | null; onSelect: (id: string) => void; canEdit: boolean; canReprocess: boolean;
}) => (
  <Card>
    <CardHeader><CardTitle>Файлы · {card.files.length}</CardTitle></CardHeader>
    <CardContent className="flex flex-col gap-3">
      {!card.files.length && <p className="text-sm text-muted-foreground">Загрузите сканы, PDF или ZIP с документами организации</p>}
      {card.files.length > 0 && (
        <div className="flex flex-col gap-2">
          <Select size="sm" aria-label="Вид документа" value={type} onChange={onType} options={types} />
          <label className="flex cursor-pointer items-center justify-between gap-2 text-sm">
            <span>Сомнительные сверху</span><Switch checked={doubtFirst} onChange={onDoubtFirst} label="Сомнительные сверху" />
          </label>
          <p className="text-xs text-muted-foreground">J / K — следующий / предыдущий документ, 1–3 — разделы</p>
        </div>
      )}
      {card.files.length > 0 && !files.length && <p className="text-sm text-muted-foreground">Документов этого вида нет</p>}
      <ul className="flex flex-col gap-3">
        {files.map((f) => (
          <li key={f.fileId} className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2">
              <FileText className="size-4 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium" title={f.name}>{f.name}{f.version > 1 ? ` · v${f.version}` : ""}</span>
              <FileActions packageId={card.packageId} fileId={f.fileId} name={f.name} canEdit={canEdit} canReprocess={canReprocess} />
            </div>
            <div className="flex flex-wrap gap-1"><StageBadge stage={f.stage} />{reasonText(f.reasonCode) && <Status tone="warning">{reasonText(f.reasonCode)}</Status>}</div>
            {f.documents.map((d) => {
              const r = docs.get(d.documentId);
              return (
                <button key={d.documentId} type="button" onClick={() => onSelect(d.documentId)}
                  className={cn("flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm outline-none transition-colors duration-fast hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring", selected === d.documentId && "bg-accent text-accent-foreground hover:bg-accent")}>
                  <span className="min-w-0 flex-1 truncate">{typeName(r?.type ?? d.type)} <span className="text-muted-foreground">стр. {d.fromPage}–{d.toPage}</span></span>
                  {d.typeConfidence !== null && <span className="text-xs tabular-nums text-muted-foreground">{Math.round(d.typeConfidence * PERCENT)}%</span>}
                  <StageBadge stage={r?.stage ?? d.stage} />
                </button>
              );
            })}
          </li>
        ))}
      </ul>
    </CardContent>
  </Card>
);

/** FE-PK-03..08, FE-SP-03..06, FE-SP-10: карточка пакета — файлы и документы по SSE, протокол, поля, решение, помощник. */
export const PackagePage = () => {
  const { id = "" } = useParams();
  const [sp, setSp] = useSearchParams();
  const { roles } = useSession();
  const qc = useQueryClient();
  const tab = (sp.get("tab") as Tab | null) ?? "protocol";
  const selected = sp.get("doc");
  const setParam = useCallback((k: string, v: string | null) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set(k, v); else n.delete(k); return n; }, { replace: true }), [setSp]);

  const card = useQuery({ queryKey: packageKeys.card(id), queryFn: () => packageApi.card(id) });
  const documents = useQuery({ queryKey: packageKeys.documents(id), queryFn: () => packageApi.documents(id) });
  const types = useQuery({ queryKey: packageKeys.types, queryFn: packageApi.types, staleTime: Infinity });
  const [doubtFirst, setDoubtFirst] = useState(true);
  const [type, setType] = useState("");
  const docs = useMemo(() => new Map((documents.data?.items ?? []).map((d) => [d.documentId, d])), [documents.data]);
  const files = useMemo(() => arrangeFiles(card.data?.files ?? [], docs, { doubtFirst, type }), [card.data, docs, doubtFirst, type]);
  const main = useRef<HTMLDivElement>(null);
  const openDoc = useCallback((documentId: string) => {
    setSp((p) => { const n = new URLSearchParams(p); n.set("doc", documentId); n.set("tab", "documents"); return n; }, { replace: true });
    // На телефоне колонки идут друг под другом: после выбора документа прокручиваем к просмотрщику
    if (!matchMedia("(min-width: 64rem)").matches) requestAnimationFrame(() => main.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }, [setSp]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (typing(e)) return;
      if (e.code === "KeyJ" || e.code === "KeyK") {
        const next = neighbourDoc(files, selected, e.code === "KeyJ" ? 1 : -1);
        if (next) { e.preventDefault(); openDoc(next); }
      } else {
        const m = DIGIT.exec(e.code);
        if (m) { e.preventDefault(); setParam("tab", TABS[Number(m[1]) - 1]); }
      }
    };
    addEventListener("keydown", onKey);
    return () => removeEventListener("keydown", onKey);
  }, [files, selected, openDoc, setParam]);

  useEventStream("package", "/stream/ticket", `/stream?packageId=${id}`, EVENTS, (name) => {
    void qc.invalidateQueries({ queryKey: packageKeys.card(id) });
    if (name !== "file.stage") void qc.invalidateQueries({ queryKey: packageKeys.documents(id) });
    if (name === "package.checked" || name === "reconnect") {
      void qc.invalidateQueries({ queryKey: protocolKeys.latest(id) });
      void qc.invalidateQueries({ queryKey: protocolKeys.versions(id) });
    }
  });

  if (card.isPending) return <RowsSkeleton rows={6} />;
  if (card.isError) return <EmptyState title="Пакет не загрузился" text={errorText(card.error)} action={<Button variant="secondary" onClick={() => card.refetch()}>Повторить</Button>} />;
  const c = card.data;
  const names = new Map((types.data?.items ?? []).map((t) => [t.code, t.name]));
  const typeName = (t: string | null) => (t ? names.get(t) ?? t : "Вид не определён");
  const doc = selected ? docs.get(selected) : undefined;
  const checked = c.status === "CHECKED" || c.status === "DECIDED";
  const canEdit = can(roles, "work") && c.status !== "DECIDED";
  const typeOptions = [{ value: "", label: "Все виды" }, ...[...new Set(c.files.flatMap((f) => f.documents.map((d) => docs.get(d.documentId)?.type ?? d.type ?? "")))]
    .filter(Boolean).map((t) => ({ value: t, label: typeName(t) }))];

  return (
    <>
      <Link to={routes.packages} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Пакеты</Link>
      <PageHeader title={c.org.name || `ИНН ${c.org.inn}`}
        sub={<span className="flex flex-wrap items-center gap-2">{c.org.name ? `ИНН ${c.org.inn} · ` : ""}{c.program} <PackageStatusBadge status={c.status} />
          <Link to={routes.org(c.org.inn)} className="inline-flex items-center gap-1 text-sm underline-offset-2 hover:underline"><History className="size-4" />история</Link></span>}
        actions={<div className="flex flex-wrap gap-2">{checked && <ExportButtons packageId={id} />}{c.status === "CHECKED" && can(roles, "decide") && <DecideButton packageId={id} />}{c.status !== "DECIDED" && c.files.length > 0 && can(roles, "reprocess") && <RebuildButton packageId={id} />}</div>} />
      {c.decision && (
        <Card className="mb-4"><CardContent className="flex flex-wrap items-center gap-2 text-sm">
          <Status tone={c.decision.decision === "APPROVE" ? "success" : c.decision.decision === "REJECT" ? "danger" : "warning"}>{DECISION[c.decision.decision]}</Status>
          <span className="text-muted-foreground">{c.decision.decidedBy} · {new Date(c.decision.decidedAt).toLocaleString("ru-RU")}</span>
          {c.decision.comment && <p className="basis-full whitespace-pre-wrap">{c.decision.comment}</p>}
        </CardContent></Card>
      )}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="flex min-w-0 flex-col gap-4">
          {canEdit && <UploadDropzone packageId={id} />}
          <Files card={c} files={files} docs={docs} typeName={typeName} types={typeOptions} type={type} onType={setType}
            doubtFirst={doubtFirst} onDoubtFirst={setDoubtFirst} selected={selected} onSelect={openDoc} canEdit={canEdit} canReprocess={can(roles, "reprocess")} />
        </div>
        <div ref={main} className="flex min-w-0 scroll-mt-20 flex-col gap-4 lg:col-span-2">
          <Segmented label="Раздел" value={tab} onChange={(v) => setParam("tab", v)}
            options={[{ value: "protocol", label: "Протокол" }, { value: "documents", label: "Документ" }, { value: "assistant", label: "Помощник" }]} />
          {tab === "protocol" && <ProtocolPanel packageId={id} onEvidence={(d) => openDoc(d)} />}
          {tab === "documents" && (doc
            ? <DocumentViewer packageId={id} doc={doc} typeName={typeName(doc.type)} />
            : <Card><EmptyState icon={<FileText />} title="Выберите документ" text="Слева — документы, найденные в файлах пакета" className="py-16" /></Card>)}
          {tab === "assistant" && <AssistantChat packageId={id} />}
        </div>
      </div>
    </>
  );
};
