import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, CheckCircle2, Pencil, XCircle, Bot, UserPen } from "lucide-react";
import type { DocumentCard, FieldCard } from "@/entities/application";
import { checkLabel, checkOk, confidenceTone, percent } from "@/entities/document";
import { FieldEditor } from "@/features/review-document";
import { Button, EmptyState, Status, Switch } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { fadeUp, stagger } from "@/shared/config/motion";

/**
 * Поля документа — «перепроверить» сверху, уверенность модели, проверки docproc, правки специалиста.
 * Правка уходит в rules (новая версия протокола); исправленное значение показываем сразу. Клавиши: J/K — поле, E — править.
 * FR-54: переключатель «только требующие проверки» скрывает поля, которые не нужно перепроверять.
 */
export const FieldsPanel = ({ packageId, doc, canAct, active, onActive }: {
  packageId: string; doc: DocumentCard; canAct: boolean; active: string | null; onActive: (f: FieldCard | null) => void;
}) => {
  const [fixed, setFixed] = useState<Record<string, string>>({});
  const all = useMemo(() => (doc.fields ?? []).map((f) => {
    const v = fixed[`${doc.id}:${f.name}`];
    return v === undefined ? f : { ...f, value: v, corrected: true, modelValue: f.value, attention: false };
  }), [doc.fields, doc.id, fixed]);
  const [onlyAttention, setOnlyAttention] = useState(false);
  const attentionCount = all.filter((f) => f.attention).length;
  const fields = useMemo(() => (onlyAttention ? all.filter((f) => f.attention) : all), [all, onlyAttention]);
  const [editing, setEditing] = useState<string | null>(null);
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && e.target.closest("input, textarea, [role=dialog]")) return;
      const i = fields.findIndex((f) => f.name === active);
      if (e.key === "j" || e.key === "k") {
        const next = fields[Math.max(0, Math.min(fields.length - 1, i + (e.key === "j" ? 1 : -1)))];
        if (next) { onActive(next); e.preventDefault(); }
      } else if (e.key === "e" && canAct && active) {
        setEditing(active); e.preventDefault();
      }
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [fields, active, canAct, onActive]);

  if (!all.length) return <EmptyState title="Полей пока нет" text={doc.stage === "UNREADABLE" || doc.stage === "FAILED" ? "Обработка завершилась с проблемой. Причина, оригинал и действия восстановления — выше" : doc.stage === "WAITING_MODEL" ? "Модель временно недоступна. Обработка продолжится автоматически" : doc.real ? "Документ ещё разбирается — см. стадию выше" : "Модель ищет документы в файле"} />;
  return (
    <div className="flex flex-col gap-2">
    {attentionCount > 0 && attentionCount < all.length && (
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>{onlyAttention ? `Показаны ${attentionCount} из ${all.length}` : `Требуют проверки: ${attentionCount} из ${all.length}`}</span>
        <label className="flex cursor-pointer items-center gap-2 font-medium text-foreground">Только требующие проверки<Switch checked={onlyAttention} onChange={setOnlyAttention} label="Только требующие проверки" /></label>
      </div>
    )}
    <motion.ul variants={stagger(0.02)} initial="hidden" animate="show" className="flex flex-col gap-1.5">
      <AnimatePresence mode="popLayout" initial={false}>
      {fields.map((f) => {
        const checks = f.checks ?? [];
        const failed = checks.filter((c) => !checkOk(c.status));
        const on = active === f.name;
        return (
          <motion.li key={f.name} layout="position" variants={fadeUp} exit="exit" onClick={() => onActive(f)}
            className={cn("cursor-pointer rounded-lg border p-3 transition-colors duration-fast",
              on ? "border-primary bg-accent/60" : f.attention ? "border-warning-border bg-warning-soft/40 hover:bg-warning-soft/60" : "border-border bg-card hover:bg-muted")}>
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                  {f.attention && <AlertTriangle className="size-3.5 text-warning" />}
                  <span className="font-medium">{f.title ?? f.name}</span>
                  {f.corrected ? <Status tone="info"><UserPen />исправлено вами</Status> : <Status tone={confidenceTone(f.confidence)}><Bot />{percent(f.confidence)}</Status>}
                </div>
                {editing === f.name ? (
                  <div className="mt-1.5" onClick={(e) => e.stopPropagation()}>
                    <FieldEditor packageId={packageId} docId={doc.id} name={f.name} value={f.value} onDone={() => setEditing(null)} onSaved={(v) => setFixed((s) => ({ ...s, [`${doc.id}:${f.name}`]: v }))} />
                  </div>
                ) : (
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <span className={cn("whitespace-pre-line break-words text-base font-medium", !f.value && "text-subtle-foreground")}>{f.value || "— не найдено"}</span>
                  </div>
                )}
                {f.corrected && f.modelValue !== undefined && f.modelValue !== f.value && <p className="mt-0.5 text-xs text-muted-foreground line-through">модель: {f.modelValue || "—"}</p>}
                {f.raw && f.raw !== f.value && <p className="mt-0.5 text-xs text-muted-foreground">в документе: «{f.raw}»</p>}
                {checks.length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {failed.map((c, i) => <Status key={`f${i}`} tone="danger" title={checkLabel(c.code)}><XCircle />{c.message || checkLabel(c.code)}</Status>)}
                    {checks.filter((c) => checkOk(c.status)).map((c, i) => <Status key={`o${i}`} tone="success" title={checkLabel(c.code)}><CheckCircle2 />{c.message || checkLabel(c.code)}</Status>)}
                  </div>
                )}
              </div>
              {canAct && editing !== f.name && (
                <Button size="icon-sm" variant="quiet" aria-label={`Править: ${f.title ?? f.name}`} onClick={(e) => { e.stopPropagation(); onActive(f); setEditing(f.name); }}><Pencil /></Button>
              )}
            </div>
          </motion.li>
        );
      })}
      </AnimatePresence>
    </motion.ul>
    </div>
  );
};
