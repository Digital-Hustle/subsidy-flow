import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilLine, Plus } from "lucide-react";
import { reviewApi, reviewKeys } from "@/entities/application";
import { protocolApi, protocolKeys, ruleTitle, type Program, type ProgramRule } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { AutoHeight, Button, DatePicker, Dialog, Field, Input, Segmented, Select, Switch, Textarea, toast } from "@/shared/ui";
import { errorTabs, hasErrors, hasShareRule, toBody, toForm, validate, type ProgramForm } from "../model/form";
import { ChecklistEditor } from "./ChecklistEditor";
import { ProgramPreview } from "./ProgramPreview";

type Tab = "main" | "docs" | "rules" | "preview";
const TABS: { value: Tab; label: string }[] = [
  { value: "main", label: "Основное" },
  { value: "docs", label: "Документы" },
  { value: "rules", label: "Проверки" },
  { value: "preview", label: "Просмотр" },
];
const SEVERITY = [{ value: "FAILED", label: "Ошибка" }, { value: "WARNING", label: "Предупреждение" }];
const BLANK = "__blank__";

const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <section className="flex flex-col gap-3"><h3 className="text-sm font-semibold">{title}</h3>{children}</section>
);

interface ProgramFormBodyProps { form: ProgramForm; set: (p: Partial<ProgramForm>) => void; creating: boolean; programs: Program[]; base: string; onBase: (code: string) => void; showErrors: boolean }

/** Поля формы по вкладкам. Значения — данные программы; проверяет и применяет их rules. */
const ProgramFormBody = ({ form, set, creating, programs, base, onBase, showErrors }: ProgramFormBodyProps) => {
  const [tab, setTab] = useState<Tab>("main");
  const types = useQuery({ queryKey: reviewKeys.types, queryFn: reviewApi.docTypes });
  const e = showErrors ? validate(form, creating) : {};
  const setRule = (code: string, p: Partial<ProgramRule>) => set({ rules: form.rules.map((r) => (r.code === code ? { ...r, ...p } : r)) });
  return (
    <div className="flex flex-col gap-5">
      <Segmented block size="sm" label="Раздел" value={tab} onChange={setTab} options={TABS} />
      <AutoHeight>
        {tab === "main" && (
          <div className="flex flex-col gap-4">
            {creating && (
              <Field label="На основе" hint="Перечень документов и проверки скопируются — их можно поправить">
                <Select value={base} onChange={onBase} options={[...programs.map((p) => ({ value: p.code, label: p.name ?? p.code })), { value: BLANK, label: "Пустая программа" }]} />
              </Field>
            )}
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Код" hint={creating ? "Например, TOURISM_2026; потом не меняется" : "Код не меняется"} error={e.code} className="sm:col-span-1">
                <Input value={form.code} disabled={!creating} onChange={(ev) => set({ code: ev.target.value.toUpperCase() })} />
              </Field>
              <Field label="Название" error={e.name} className="sm:col-span-2"><Input value={form.name} onChange={(ev) => set({ name: ev.target.value })} /></Field>
            </div>
            <Field label="Описание для заявителя" error={e.description} hint="Кому подходит программа и на что дают субсидию">
              <Textarea rows={4} value={form.description} onChange={(ev) => set({ description: ev.target.value })} />
            </Field>
            <Field label="Приём заявок до" hint="Включительно. Пусто — без срока">
              <DatePicker value={form.deadline} onChange={(deadline) => set({ deadline })} placeholder="Без срока" />
            </Field>
            {form.deadline && <Button size="sm" variant="quiet" className="self-start" onClick={() => set({ deadline: "" })}>Убрать срок</Button>}
          </div>
        )}
        {tab === "docs" && (
          <div className="flex flex-col gap-3">
            {types.isError && <p className="text-sm text-danger">Виды документов не загрузились: {errorText(types.error)}</p>}
            {e.checklist && <p className="text-sm text-danger">{e.checklist}</p>}
            <ChecklistEditor rows={form.checklist} types={types.data ?? []} errors={e.rows} onChange={(checklist) => set({ checklist })} />
          </div>
        )}
        {tab === "rules" && (
          <div className="flex flex-col gap-5">
            <Section title="Суммы и софинансирование">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Субсидия от, ₽" error={e.amountMin} hint="Пусто — без нижней границы"><Input inputMode="decimal" value={form.amountMin} onChange={(ev) => set({ amountMin: ev.target.value })} /></Field>
                <Field label="Субсидия до, ₽" error={e.amountMax} hint="Лимит на одного заявителя"><Input inputMode="decimal" value={form.amountMax} onChange={(ev) => set({ amountMax: ev.target.value })} /></Field>
              </div>
              {hasShareRule(form) ? (
                <Field label="Собственные средства, не менее, %" error={e.ownShare} hint="Доля софинансирования от расходов проекта">
                  <Input inputMode="decimal" value={form.ownShare} onChange={(ev) => set({ ownShare: ev.target.value })} />
                </Field>
              ) : <p className="text-sm text-muted-foreground">Проверки доли софинансирования нет — создайте программу на основе существующей, чтобы её получить.</p>}
            </Section>
            <Section title="Проверки документов">
              {!form.rules.length ? <p className="text-sm text-muted-foreground">Проверок нет. Сервер проверит только комплектность.</p> : (
                <ul className="flex flex-col gap-2">
                  {form.rules.map((r) => (
                    <li key={r.code} className="flex flex-wrap items-center gap-3 rounded-md border border-border px-3 py-2">
                      <Switch checked={r.enabled !== false} onChange={(on) => setRule(r.code, { enabled: on })} />
                      <span className="min-w-0 flex-1 text-sm">{r.title ?? ruleTitle(r.code)}<span className="block text-xs text-muted-foreground">{r.code}</span></span>
                      <Segmented size="sm" className="w-full sm:w-auto" block label="Строгость" value={r.severity === "WARNING" ? "WARNING" : "FAILED"} options={SEVERITY} onChange={(severity) => setRule(r.code, { severity })} />
                    </li>
                  ))}
                </ul>
              )}
            </Section>
          </div>
        )}
        {tab === "preview" && <ProgramPreview form={form} />}
      </AutoHeight>
    </div>
  );
};

interface ProgramDialogProps { program: Program | null; programs: Program[]; open: boolean; onClose: () => void }

/** Создание (POST) или новая версия (PUT) программы; форма вместо сырого JSON. */
const ProgramDialog = ({ program, programs, open, onClose }: ProgramDialogProps) => {
  const qc = useQueryClient();
  const creating = !program;
  const [base, setBase] = useState(programs[0]?.code ?? BLANK);
  const [form, setForm] = useState<ProgramForm>(() => toForm(program, program ?? programs[0] ?? null));
  const [showErrors, setShowErrors] = useState(false);
  const set = (p: Partial<ProgramForm>) => setForm((f) => ({ ...f, ...p }));
  const onBase = (code: string) => {
    setBase(code);
    const src = programs.find((p) => p.code === code) ?? null;
    setForm((f) => ({ ...toForm(null, src), code: f.code, name: f.name, description: f.description, deadline: f.deadline }));
  };
  const save = useMutation({
    mutationFn: () => {
      const body = toBody(form);
      return creating ? protocolApi.createProgram(body) : protocolApi.saveProgram(program.code, { name: body.name, params: body.params, checklist: body.checklist });
    },
    onSuccess: (p) => {
      toast.success(creating ? `Программа «${p.name ?? p.code}» создана` : "Сохранена новая версия программы");
      void qc.invalidateQueries({ queryKey: protocolKeys.programs });
      void qc.invalidateQueries({ queryKey: protocolKeys.programVersions(p.code) });
      onClose();
    },
  });
  const submit = () => {
    setShowErrors(true);
    if (!hasErrors(validate(form, creating))) save.mutate();
  };
  const codeTaken = save.error instanceof ApiError && save.error.code === "PROGRAM_EXISTS";
  return (
    <Dialog open={open} onClose={onClose} className="sm:max-w-3xl" title={creating ? "Новая программа" : `Программа ${program.code}`}
      description={creating ? "Сразу появится в «Новой заявке» и в кабинете заявителя" : "Новая версия применится к следующим проверкам; решённые пакеты не меняются"}
      footer={<Button disabled={save.isPending} onClick={submit}>{save.isPending ? "Сохраняем…" : creating ? "Создать программу" : "Сохранить версию"}</Button>}>
      <div className="flex flex-col gap-4">
        <ProgramFormBody form={form} set={set} creating={creating} programs={programs} base={base} onBase={onBase} showErrors={showErrors} />
        {showErrors && hasErrors(validate(form, creating)) && <p className="text-sm text-danger">Проверьте вкладки: {errorTabs(validate(form, creating)).map((t) => TABS.find((x) => x.value === t)?.label).join(", ")}</p>}
        {save.isError && <p className="text-sm text-danger">{codeTaken ? "Программа с таким кодом уже есть — выберите другой код" : errorText(save.error)}</p>}
      </div>
    </Dialog>
  );
};

/** Кнопка «Новая программа» (старший специалист). */
export const CreateProgramButton = ({ programs }: { programs: Program[] }) => {
  const [open, setOpen] = useState(false);
  const [n, setN] = useState(0);
  return (
    <>
      <Button onClick={() => { setN((x) => x + 1); setOpen(true); }}><Plus />Новая программа</Button>
      <ProgramDialog key={n} program={null} programs={programs} open={open} onClose={() => setOpen(false)} />
    </>
  );
};

/** Кнопка «Изменить» — новая версия программы. */
export const EditProgramButton = ({ program, programs }: { program: Program; programs: Program[] }) => {
  const [open, setOpen] = useState(false);
  const [n, setN] = useState(0);
  return (
    <>
      <Button size="sm" variant="secondary" onClick={() => { setN((x) => x + 1); setOpen(true); }}><PencilLine />Изменить</Button>
      <ProgramDialog key={n} program={program} programs={programs} open={open} onClose={() => setOpen(false)} />
    </>
  );
};