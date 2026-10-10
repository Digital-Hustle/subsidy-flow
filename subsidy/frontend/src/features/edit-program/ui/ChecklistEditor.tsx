import { AnimatePresence, motion } from "motion/react";
import { Plus, Trash2 } from "lucide-react";
import type { DocType } from "@/entities/application";
import { typeTitle } from "@/entities/document";
import { Button, Chip, Field, Input, Segmented, Switch } from "@/shared/ui";
import { fadeUp } from "@/shared/config/motion";
import { emptyRow, type ChecklistKind, type ChecklistRow } from "../model/form";

const KIND_OPTIONS: { value: ChecklistKind; label: string }[] = [
  { value: "REQUIRED", label: "Обязательный" },
  { value: "CONDITIONAL", label: "По условию" },
  { value: "ONE_OF", label: "Один из" },
];

interface ChecklistEditorProps {
  rows: ChecklistRow[];
  types: DocType[];
  errors?: Record<string, string>;
  onChange: (rows: ChecklistRow[]) => void;
}

/** Перечень документов программы: вид из реестра docproc, обязательность, условие и подсказка заявителю. */
export const ChecklistEditor = ({ rows, types, errors = {}, onChange }: ChecklistEditorProps) => {
  const patch = (key: string, p: Partial<ChecklistRow>) => onChange(rows.map((r) => (r.key === key ? { ...r, ...p } : r)));
  const toggleType = (r: ChecklistRow, code: string) => {
    const multi = r.kind === "ONE_OF";
    const has = r.types.includes(code);
    const next = multi ? (has ? r.types.filter((t) => t !== code) : [...r.types, code]) : has ? [] : [code];
    const autoTitle = !r.title.trim() || r.title === typeTitle(r.types[0]);
    patch(r.key, { types: next, title: autoTitle && next.length === 1 ? (types.find((t) => t.code === next[0])?.name ?? typeTitle(next[0])) : r.title });
  };
  const options = types.filter((t) => t.code !== "OTHER");
  return (
    <div className="flex flex-col gap-3">
      <AnimatePresence initial={false}>
        {rows.map((r, i) => (
          <motion.div key={r.key} layout variants={fadeUp} initial="hidden" animate="show" exit="hidden" className="flex flex-col gap-3 rounded-lg border border-border p-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium text-muted-foreground">Документ {i + 1}</span>
              <Button size="sm" variant="ghost" className="ml-auto sm:order-last" aria-label="Убрать документ" onClick={() => onChange(rows.filter((x) => x.key !== r.key))}><Trash2 /></Button>
              <Segmented size="sm" block className="w-full sm:ml-auto sm:w-auto" label="Обязательность" value={r.kind} options={KIND_OPTIONS}
                onChange={(kind) => patch(r.key, { kind, types: kind === "ONE_OF" ? r.types : r.types.slice(0, 1) })} />
            </div>
            <div className="flex flex-col gap-1.5">
              <span className="text-xs text-muted-foreground">{r.kind === "ONE_OF" ? "Виды документов (подойдёт любой)" : "Вид документа"}</span>
              <div className="flex flex-wrap gap-1.5">
                {options.map((t) => (
                  <Chip key={t.code} pressed={r.types.includes(t.code)} onClick={() => toggleType(r, t.code)}>{t.name ?? typeTitle(t.code)}</Chip>
                ))}
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Название для заявителя"><Input value={r.title} onChange={(e) => patch(r.key, { title: e.target.value })} /></Field>
              <Field label={r.kind === "CONDITIONAL" ? "Когда нужен" : "Подсказка"} hint={r.kind === "CONDITIONAL" ? "Например: если подписант — не руководитель" : "Что важно в документе"}>
                <Input value={r.hint} onChange={(e) => patch(r.key, { hint: e.target.value })} />
              </Field>
            </div>
            <div className="flex items-center gap-2 text-sm"><Switch checked={r.single} onChange={(single) => patch(r.key, { single })} label="Только один такой документ в пакете" /><span>Только один такой документ в пакете</span></div>
            {errors[r.key] && <p className="text-sm text-danger">{errors[r.key]}</p>}
          </motion.div>
        ))}
      </AnimatePresence>
      <Button variant="secondary" className="self-start" onClick={() => onChange([...rows, emptyRow()])}><Plus />Добавить документ</Button>
    </div>
  );
};