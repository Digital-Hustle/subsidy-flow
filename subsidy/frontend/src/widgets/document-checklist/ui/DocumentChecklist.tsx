import { motion, AnimatePresence, LayoutGroup } from "motion/react";
import { AlertTriangle, CheckCircle2, FileText, Trash2, ArrowRightLeft, CircleDashed, Loader2, PenLine } from "lucide-react";
import { type ApplicantApplication, type ApplicantDocument, type RequiredDocument } from "@/entities/application";
import { StageBadge, reasonView, stageView } from "@/entities/document";
import { useUpload, useRemoveDocument, UploadButtons } from "@/features/upload-document";
import { Card, Progress, Status, Button, type Tone } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { fadeUp, spring } from "@/shared/config/motion";

const EXTRA = "__extra";
const typeOf = (d: ApplicantDocument) => d.detectedType && d.detectedType !== "UNKNOWN" ? d.detectedType : d.slotType ?? EXTRA;
const SLOT_ICON = { MISSING: CircleDashed, PROCESSING: Loader2, DONE: CheckCircle2, PROBLEM: AlertTriangle } as const;
type SlotState = keyof typeof SLOT_ICON;
const SLOT_VIEW: Record<SlotState, { label: string; tone: Tone }> = {
  MISSING: { label: "Не загружен", tone: "neutral" },
  PROCESSING: { label: "Проверяем", tone: "info" },
  DONE: { label: "Принят", tone: "success" },
  PROBLEM: { label: "Нужно исправить", tone: "danger" },
};
/** Состояние пункта считаем по документам в нём: проблема важнее обработки, обработка важнее готовности. */
const slotState = (list: ApplicantDocument[]): SlotState => {
  if (!list.length) return "MISSING";
  const views = list.map((d) => stageView(d.stage));
  if (views.some((v) => v.problem)) return "PROBLEM";
  if (views.some((v) => !v.done)) return "PROCESSING";
  return "DONE";
};

/** Карточка загруженного документа: стадия по SSE, причина и подсказка, «перенесли в другой пункт», замена/удаление. */
const DocCard = ({ d, titleOf, up, locked }: { d: ApplicantDocument; titleOf: (t?: string) => string; up: ReturnType<typeof useUpload>; locked: boolean }) => {
  const st = stageView(d.stage);
  const reason = d.message ? { text: d.message, hint: d.hint ?? undefined } : reasonView(d.reasonCode);
  const moved = d.slotType && d.detectedType && d.detectedType !== "UNKNOWN" && d.slotType !== d.detectedType;
  const err = up.errorFor(d.id);
  const busy = up.pendingFor === d.id;
  return (
    <motion.div layout layoutId={d.id} transition={spring.soft} className="rounded-lg border border-border bg-card p-3">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-md bg-surface text-muted-foreground"><FileText className="size-5" /></span>
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <StageBadge stage={d.stage} className="self-start" />
          <span className="truncate text-sm font-medium">{d.fileName ?? "Документ"}</span>
          <span className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
            {d.pages ? <span>{d.pages} стр.</span> : null}
            {d.electronicSignature ? <span className="inline-flex items-center gap-1 text-success"><PenLine className="size-3" />есть электронная подпись</span> : null}
          </span>
          {!st.done && <Progress value={st.progress} className="h-1" />}
        </div>
      </div>
      {moved && (
        <p className="mt-3 flex items-start gap-2 rounded-md bg-info-soft px-3 py-2 text-sm text-info-soft-foreground">
          <ArrowRightLeft className="mt-0.5 size-4 shrink-0" />Это «{titleOf(d.detectedType ?? undefined)}» — перенесли в нужный пункт
        </p>
      )}
      {reason && d.reasonCode !== "TYPE_MOVED" && (
        <div className={cn("mt-3 rounded-md px-3 py-2 text-sm", st.problem ? "bg-danger-soft text-danger-soft-foreground" : "bg-warning-soft text-warning-soft-foreground")}>
          <p className="font-medium">{reason.text}</p>{reason.hint && <p className="mt-0.5 opacity-90">{reason.hint}</p>}
        </div>
      )}
      {err && <p className="mt-3 rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground"><b className="font-medium">{err.text}</b>{err.hint && <span className="block">{err.hint}</span>}</p>}
      {!locked && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {st.problem
            ? <UploadButtons size="sm" label="Загрузить исправленный" disabled={busy} onFile={(f) => up.upload({ file: f, docId: d.id, mode: "replace" })} />
            : <UploadButtonsReplace busy={busy} onFile={(f) => up.upload({ file: f, docId: d.id, mode: "replace" })} />}
        </div>
      )}
    </motion.div>
  );
};

const UploadButtonsReplace = ({ busy, onFile }: { busy: boolean; onFile: (f: File) => void }) => (
  <span className="[&_button]:text-muted-foreground"><UploadButtons size="sm" variant="quiet" label="Заменить файл" disabled={busy} onFile={onFile} /></span>
);

const RemoveButton = ({ applicationId, docId }: { applicationId: string; docId: string }) => {
  const rm = useRemoveDocument(applicationId);
  return <Button size="icon-sm" variant="quiet" aria-label="Удалить документ" disabled={rm.isPending} onClick={() => rm.mutate(docId)}><Trash2 /></Button>;
};

/** Список документов программы (считает сервер), загрузка файла в пункт, стадии разбора по SSE. */
export const DocumentChecklist = ({ app, titleOf }: { app: ApplicantApplication; titleOf: (t?: string) => string }) => {
  const id = app.applicationId;
  const up = useUpload(id);
  const locked = !(app.status === "DRAFT" || app.status === "NEEDS_FIX");
  const required: RequiredDocument[] = app.requiredDocuments ?? [];
  const docs = app.documents ?? [];
  const known = new Set(required.map((r) => r.type));
  const extra = docs.filter((d) => !known.has(typeOf(d)));
  const missing = new Set(app.missing ?? []);
  const stateOf = (r: RequiredDocument): SlotState => {
    const st = slotState(docs.filter((d) => typeOf(d) === r.type));
    return st === "DONE" && missing.has(r.type) ? "PROCESSING" : st;
  };
  const mandatory = required.filter((r) => r.required !== false);
  const done = mandatory.filter((r) => stateOf(r) === "DONE").length;
  const total = mandatory.length;

  const slot = (type: string, title: string, hint: string | null | undefined, state: SlotState, req: boolean, list: ApplicantDocument[]) => {
    const v = SLOT_VIEW[state];
    const Icon = SLOT_ICON[state];
    const err = up.errorFor(type);
    const warn = up.warningsFor(type);
    return (
      <motion.li key={type} layout variants={fadeUp} className="list-none">
        <Card className="p-4 sm:p-5">
          <div className="flex items-start gap-3">
            <Icon className={cn("mt-0.5 size-5 shrink-0", state === "DONE" ? "text-success" : state === "PROBLEM" ? "text-danger" : "text-muted-foreground")} />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{title}</h3>{!req && <Status>по желанию</Status>}</div>
              {hint && <p className="mt-0.5 text-sm text-muted-foreground">{hint}</p>}
            </div>
            {type !== EXTRA && <Status tone={v.tone} className="max-sm:hidden">{v.label}</Status>}
          </div>
          {list.length > 0 && (
            <div className="mt-4 flex flex-col gap-2">
              <AnimatePresence initial={false}>
                {list.map((d) => (
                  <div key={d.id} className="relative">
                    <DocCard d={d} titleOf={titleOf} up={up} locked={locked} />
                    {!locked && <span className="absolute right-2 top-2"><RemoveButton applicationId={id} docId={d.id} /></span>}
                  </div>
                ))}
              </AnimatePresence>
            </div>
          )}
          {err && <div className="mt-4 rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground" role="alert"><p className="font-medium">{err.text}</p>{err.hint && <p>{err.hint}</p>}</div>}
          {warn && <p className="mt-3 text-sm text-warning-soft-foreground">{warn.join(". ")}</p>}
          {!locked && (list.length === 0 || state === "PROBLEM" || type === EXTRA) && (
            <div className="mt-4">
              <UploadButtons size="md" label={err ? "Загрузить другой файл" : list.length ? "Ещё документ" : "Загрузить файл"} disabled={up.pendingFor === type}
                onFile={(f) => up.upload({ file: f, slotType: type === EXTRA ? undefined : type })} />
              {up.pendingFor === type && <p className="mt-2 text-sm text-muted-foreground">Отправляем файл…</p>}
            </div>
          )}
        </Card>
      </motion.li>
    );
  };

  return (
    <div className="flex flex-col gap-4">
      <Card className="p-4 sm:p-5">
        <div className="flex items-baseline justify-between gap-3"><span className="font-semibold">Обязательные документы</span><span className="text-sm text-muted-foreground">{done} из {total}</span></div>
        <Progress value={total ? done / total : 0} className="mt-3" />
      </Card>
      <LayoutGroup>
        <motion.ul initial="hidden" animate="show" className="flex flex-col gap-3">
          {required.map((r) => slot(r.type, r.title ?? titleOf(r.type), r.hint ?? r.condition, stateOf(r), r.required !== false, docs.filter((d) => typeOf(d) === r.type)))}
          {slot(EXTRA, "Другие документы", "Если документ не подходит ни под один пункт — загрузите сюда, вид определим сами", extra.length ? slotState(extra) : "MISSING", false, extra)}
        </motion.ul>
      </LayoutGroup>
    </div>
  );
};
