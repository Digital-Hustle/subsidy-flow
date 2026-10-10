import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, ArrowLeft, Bot, Building2, ClipboardList, FileText, Gavel, History, ListChecks, MessageSquarePlus, MessageSquareWarning, RefreshCw, ScrollText, UserCheck } from "lucide-react";
import { AppStatusBadge, dateRuFull, dateTimeRu, decisionView, moneyRu, reviewApi, reviewKeys, waitingRu, type ApplicationData, type FieldCard } from "@/entities/application";
import { StageBadge, confidenceTone, percent, reasonView, typeTitle } from "@/entities/document";
import { protocolApi, protocolKeys, useProgramName } from "@/entities/protocol";
import { can, useSession } from "@/entities/session";
import { AssistantChat, AssistantFab } from "@/features/ask-assistant";
import { DecisionDialog } from "@/features/decide-application";
import { RequestDialog } from "@/features/request-resubmit";
import { DocumentActions, useAssign, useRebuild } from "@/features/review-document";
import { ExportProtocolButtons } from "@/features/export-documents";
import { useTakeNext } from "@/features/take-next";
import { DocumentViewer } from "@/widgets/document-viewer";
import { UploadFiles } from "@/features/create-package";
import { FieldsPanel } from "@/widgets/fields-panel";
import { HistoryTimeline } from "@/widgets/history-timeline";
import { ProtocolPanel } from "@/widgets/protocol-panel";
import { AnimatedNumber, Button, Card, EmptyState, CardSkeleton, HeaderSkeleton, Skeleton, TilesSkeleton, Segmented, Status } from "@/shared/ui";
import { ApiError, errorText, useEventStream } from "@/shared/api";
import { cn } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp, popIn, spring, stagger, tween } from "@/shared/config/motion";

type Tab = "fields" | "protocol" | "application" | "history" | "assistant";

const ApplicationData = ({ a }: { a: ApplicationData }) => {
  const rows: [string, string | null | undefined][] = [
    ["Организация", a.orgName], ["ИНН", a.orgInn], ["ОГРН / ОГРНИП", a.ogrn], ["Проект", a.project],
    ["Запрашиваемая сумма", a.requestedAmount ? moneyRu(a.requestedAmount) : null], ["Дата заявления", a.applicationDate ? dateRuFull(a.applicationDate) : null],
    ["Адрес", [a.address?.value, a.address?.purpose].filter(Boolean).join(" · ")],
    ["Подписант", [a.signatory?.fullName, a.signatory?.position].filter(Boolean).join(", ")], ["Основание полномочий", a.signatory?.authority],
  ];
  return (
    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
      {rows.map(([k, v]) => <div key={k} className="min-w-0"><dt className="text-xs text-muted-foreground">{k}</dt><dd className={cn("break-words text-sm font-medium", !v && "text-subtle-foreground")}>{v || "—"}</dd></div>)}
    </dl>
  );
};

/** Карточка заявки: оригинал рядом с полями, протокол проверки, история, помощник и действия специалиста. */
export const ApplicationPage = () => {
  const programName = useProgramName();
  const { id = "" } = useParams();
  const s = useSession();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: reviewKeys.card(id), queryFn: () => reviewApi.card(id) });
  const [docId, setDocId] = useState<string | null>(null);
  const [field, setField] = useState<FieldCard | null>(null);
  const [jump, setJump] = useState<{ page: number } | null>(null);
  const [tab, setTab] = useState<Tab>("fields");
  const [decide, setDecide] = useState(false);
  const [ask, setAsk] = useState(false); // смонтирован (форма сбрасывается при каждом открытии)
  const [askOpen, setAskOpen] = useState(false);
  const assign = useAssign(id);
  const rebuild = useRebuild(id);
  const next = useTakeNext();
  useEventStream("package", "/stream/ticket", `/stream?packageId=${id}`, ["file.stage", "document.parsed", "package.checked", "package.changed"], () => {
    void qc.invalidateQueries({ queryKey: reviewKeys.card(id) });
    void qc.invalidateQueries({ queryKey: protocolKeys.latest(id) });
    void qc.invalidateQueries({ queryKey: protocolKeys.versions(id) });
  });
  const proto = useQuery({ queryKey: protocolKeys.latest(id), queryFn: () => protocolApi.latest(id), retry: false });
  const card = q.data;
  const docs = useMemo(() => card?.documents ?? [], [card]);
  const doc = docs.find((d) => d.id === docId) ?? docs.find((d) => d.attention) ?? docs[0];
  const canAct = can(s.roles, "act");
  useEffect(() => { setField(null); }, [doc?.id]);
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && e.target.closest("input, textarea, button, [role=dialog]")) return;
      if (e.key === "n" && !next.isPending) next.mutate();
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [next]);

  if (q.isPending) return (
    <div aria-busy="true" aria-label="Загрузка заявки">
      <HeaderSkeleton />
      <TilesSkeleton count={4} className="mb-4" />
      <div className="grid gap-4 lg:grid-cols-2"><Skeleton className="h-96 rounded-xl lg:h-svh" /><div className="flex flex-col gap-3"><CardSkeleton lines={3} /><CardSkeleton lines={6} /></div></div>
    </div>
  );
  if (q.isError || !card) {
    const nf = q.error instanceof ApiError && q.error.status === 404;
    return <EmptyState title={nf ? "Заявка не найдена" : "Карточка не загрузилась"} text={nf ? undefined : errorText(q.error)}
      action={<div className="flex gap-2"><Link to={routes.operator}><Button variant="secondary"><ArrowLeft />К очереди</Button></Link>{!nf && <Button onClick={() => q.refetch()}>Повторить</Button>}</div>} />;
  }
  const sum = card.summary;
  const mine = !!card.assignee && (card.assignee === s.login || card.assignee === s.id || card.assignee === s.name);
  const decided = card.status === "DECIDED";
  const openRequests = card.requests.filter((r) => !r.resolvedAt);
  const titleOf = (t: string) => docs.find((d) => d.type === t)?.typeName ?? typeTitle(t);
  const reason = doc ? reasonView(doc.reasonCode) : null;
  const orgTitle = card.org.name || card.application?.orgName || (card.org.inn ? `ИНН ${card.org.inn}` : "Организация");
  const onEvidence = (documentId: string, page: number | null, fieldName: string | null) => {
    const d = docs.find((x) => x.id === documentId);
    if (!d) return;
    setDocId(d.id);
    setTab("fields");
    if (page && page >= d.fromPage && page <= d.toPage) setJump({ page: page - d.fromPage + 1 });
    const f = fieldName ? d.fields.find((x) => x.name === fieldName) : null;
    setTimeout(() => setField(f ?? null), 0);
  };
  const tabs: { value: Tab; label: string; icon: React.ReactNode }[] = [
    { value: "fields", label: "Поля", icon: <ListChecks /> },
    { value: "protocol", label: "Протокол", icon: <ScrollText /> },
    ...(card.application ? [{ value: "application" as Tab, label: "Анкета", icon: <ClipboardList /> }] : []),
    { value: "history", label: "История", icon: <History /> },
    ...(can(s.roles, "assistant") ? [{ value: "assistant" as Tab, label: "Помощник", icon: <Bot /> }] : []),
  ];
  const tiles = [
    { label: "Комплект", value: sum?.completeness ?? 0, suffix: "%", brand: true, hint: "Обязательные документы на месте" },
    { label: "Ошибок", value: sum?.failed ?? 0, tone: (sum?.failed ?? 0) > 0 ? "text-danger" : "" },
    { label: "Предупреждений", value: sum?.warnings ?? 0, tone: (sum?.warnings ?? 0) > 0 ? "text-warning" : "" },
    { label: "Проверить вручную", value: sum?.needsReview ?? 0, tone: (sum?.needsReview ?? 0) > 0 ? "text-info" : "" },
  ];

  return (
    <>
      <motion.div variants={fadeUp} initial="hidden" animate="show" className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <Link to={routes.operator} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Очередь</Link>
          <h1 className="truncate font-display text-2xl font-semibold tracking-display sm:text-3xl">{orgTitle}</h1>
          <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <AppStatusBadge status={card.status} />
            <span>№ {id.slice(0, 8)}</span>
            {card.program && <span>{programName(card.program)}</span>}
            {card.org.inn && <Link to={routes.operatorOrganization(card.org.inn)} className="inline-flex items-center gap-1 hover:text-foreground hover:underline"><Building2 className="size-3.5" />ИНН {card.org.inn}</Link>}
            {card.submittedAt && <span>отправлена {dateTimeRu(card.submittedAt)}</span>}
            {card.assignee && <Status><UserCheck />{mine ? "у вас" : card.assignee}</Status>}
            {card.contact?.phone && <span>{card.contact.phone}</span>}
            {card.contact?.email && <span>{card.contact.email}</span>}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {canAct && !mine && !decided && <Button variant="secondary" disabled={assign.isPending} onClick={() => assign.mutate(!!card.assignee)}><UserCheck />{card.assignee ? "Забрать себе" : "Взять в работу"}</Button>}
          {canAct && !decided && <Button variant="secondary" onClick={() => { setAsk(true); setAskOpen(true); }}><MessageSquarePlus />Запросить</Button>}
          {canAct && !decided && docs.length > 0 && <UploadFiles packageId={id} label="Добавить файлы" />}
          {can(s.roles, "export") && sum && <ExportProtocolButtons packageId={id} />}
          {can(s.roles, "reprocess") && <Button variant="quiet" disabled={rebuild.isPending} onClick={() => rebuild.mutate(undefined)} title="Пересобрать документы из docproc и пересчитать протокол"><RefreshCw />Пересобрать</Button>}
          {can(s.roles, "decide") && !decided && <Button onClick={() => setDecide(true)} disabled={!sum} title={sum ? undefined : "Решение — после формирования протокола"}><Gavel />Решение</Button>}
        </div>
      </motion.div>

      <motion.dl variants={stagger(0.05)} initial="hidden" animate="show" className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {tiles.map((k) => (
          <motion.div key={k.label} variants={popIn} title={k.hint} className={cn("relative flex min-w-0 flex-col gap-0.5 overflow-hidden rounded-xl px-4 py-3 shadow-card sm:px-5 sm:py-4", k.brand ? "bg-brand-deep text-white" : "bg-card")}>
            {k.brand && <span aria-hidden className="absolute inset-0 bg-sheen" />}
            <dt className={cn("relative truncate text-xs", k.brand ? "text-white/85" : "text-muted-foreground")}>{k.label}</dt>
            <dd className={cn("relative font-display text-3xl font-semibold tabular-nums tracking-hero", k.tone)}>{sum ? <><AnimatedNumber value={k.value} />{k.suffix}</> : "—"}</dd>
          </motion.div>
        ))}
      </motion.dl>

      {(card.decision || openRequests.length > 0) && (
        <Card className="mb-4 p-4">
          {card.decision && (
            <p className="flex flex-wrap items-center gap-2 text-sm">
              <Status tone={decisionView(card.decision.decision).tone}>{decisionView(card.decision.decision).label}</Status>
              <span className="text-muted-foreground">{card.decision.decidedBy} · {dateTimeRu(card.decision.decidedAt)}</span>
              {card.decision.comment && <span>— «{card.decision.comment}»</span>}
            </p>
          )}
          {openRequests.length > 0 && (
            <div className={card.decision ? "mt-3" : undefined}>
              <p className="mb-1.5 flex items-center gap-2 text-sm font-semibold"><MessageSquareWarning className="size-4 text-warning" />Ждём от заявителя</p>
              <ul className="flex list-disc flex-col gap-1 pl-6 text-sm">
                {openRequests.map((r, i) => <li key={r.id ?? i}>{r.type ? <b className="font-medium">{titleOf(r.type)}: </b> : null}{r.reason} <span className="text-xs text-muted-foreground">· {dateTimeRu(r.createdAt)}</span></li>)}
              </ul>
            </div>
          )}
        </Card>
      )}

      {!docs.length ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card><EmptyState icon={<FileText />} title="Документов пока нет" text={card.source === "SPECIALIST" ? "Загрузите документы организации: PDF, DOCX, XLSX, сканы или ZIP" : "Заявитель ещё не загрузил файлы"}
            action={canAct && !decided ? <UploadFiles packageId={id} /> : undefined} /></Card>
          <ProtocolPanel packageId={id} onEvidence={onEvidence} />
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="lg:sticky lg:top-20 lg:h-svh lg:self-start lg:pb-24">
            <Card className="h-96 overflow-hidden sm:h-svh lg:h-full">{doc && <DocumentViewer doc={doc} highlight={jump} />}</Card>
          </div>
          <div className="flex min-w-0 flex-col gap-3">
            <Segmented label="Документы" value={doc?.id ?? ""} onChange={(v) => { setDocId(v); setJump(null); }}
              options={docs.map((d) => ({ value: d.id, icon: d.attention ? <AlertTriangle className="text-warning" /> : undefined,
                label: d.typeName ?? (d.type ? typeTitle(d.type) : d.real ? "Вид не определён" : d.fileName ?? "Файл") }))} />
            <AnimatePresence mode="wait" initial={false}>
            {doc && (
              <motion.div key={doc.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}>
              <Card className="p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-display text-lg font-semibold">{doc.typeName ?? (doc.type ? typeTitle(doc.type) : doc.real ? "Вид не определён" : "Файл разбирается")}</h2>
                  {doc.typeConfidence != null && <Status tone={confidenceTone(doc.typeConfidence)}>вид {percent(doc.typeConfidence)}</Status>}
                  <StageBadge stage={doc.stage} />
                </div>
                <p className="mt-1 text-xs text-muted-foreground">{doc.fileName ?? "Файл"}{doc.real ? ` · стр. ${doc.fromPage === doc.toPage ? doc.fromPage : `${doc.fromPage}–${doc.toPage}`}` : ""}{doc.source === "SPECIALIST" ? " · вид указал специалист" : ""}</p>
                {reason && <p className="mt-2 rounded-md bg-warning-soft px-3 py-2 text-sm text-warning-soft-foreground"><b className="font-medium">{reason.text}</b>{reason.hint ? ` — ${reason.hint}` : ""}</p>}
                {doc.evidence && <p className="mt-2 text-xs text-muted-foreground">Почему этот вид: {doc.evidence}</p>}
                {doc.model && <p className="mt-1 text-xs text-muted-foreground">Модель {doc.model}{doc.promptVersion ? ` · промпт ${doc.promptVersion}` : ""}</p>}
                <div className="mt-3 empty:hidden"><DocumentActions packageId={id} doc={doc} canAct={canAct && !decided} canReprocess={can(s.roles, "reprocess") && !decided} /></div>
              </Card>
              </motion.div>
            )}
            </AnimatePresence>
            <Segmented value={tab} onChange={setTab} label="Раздел" options={tabs} />
            <AnimatePresence mode="wait" initial={false}>
              <motion.div key={tab === "fields" ? `f-${doc?.id}` : tab} className="flex flex-col gap-3" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}>
                {tab === "fields" && doc && (
                  <>
                    <FieldsPanel packageId={id} doc={doc} canAct={canAct && !decided} active={field?.name ?? null} onActive={setField} />
                    <p className="hidden text-xs text-muted-foreground lg:block">Клавиши: J/K — поле, E — править, N — следующая заявка.{card.submittedAt ? ` Ждёт ${waitingRu((Date.now() - Date.parse(card.submittedAt)) / 1000)}` : ""}</p>
                  </>
                )}
                {tab === "protocol" && <ProtocolPanel packageId={id} onEvidence={onEvidence} />}
                {tab === "application" && card.application && <Card className="p-4"><ApplicationData a={card.application} /></Card>}
                {tab === "history" && <Card className="p-4"><HistoryTimeline packageId={id} inn={card.org.inn} /></Card>}
                {tab === "assistant" && <AssistantChat packageId={id} />}
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      )}
      {can(s.roles, "assistant") && tab !== "assistant" && <AssistantFab packageId={id} onExpand={() => setTab("assistant")} />}
      <DecisionDialog packageId={id} open={decide} onClose={() => setDecide(false)} />
      {ask && <RequestDialog packageId={id} docs={docs} missing={(proto.data?.completeness?.missing ?? []).map((m) => ({ type: m.type, title: titleOf(m.type) }))} open={askOpen} onClose={() => setAskOpen(false)} onClosed={() => setAsk(false)} />}
    </>
  );
};
