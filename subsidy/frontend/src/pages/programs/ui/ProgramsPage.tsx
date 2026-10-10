import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ChevronDown, PencilLine, ScrollText } from "lucide-react";
import { protocolApi, protocolKeys, type Program } from "@/entities/protocol";
import { can, useSession } from "@/entities/session";
import { errorText } from "@/shared/api";
import { AutoHeight, Button, Card, CardContent, CardHeader, CardTitle, Dialog, EmptyState, Field, Input, PageHeader, RowsSkeleton, Status, Textarea, toast } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { fadeUp, stagger } from "@/shared/config/motion";

const JSON_INDENT = 2;
const pretty = (v: unknown) => JSON.stringify(v, null, JSON_INDENT);
const parse = (s: string): { ok: true; v: unknown } | { ok: false } => { try { return { ok: true, v: JSON.parse(s) as unknown }; } catch { return { ok: false }; } };

/** Правка программы (SENIOR): параметры и перечень — JSON, проверяет и версионирует rules. */
const EditProgram = ({ p }: { p: Program }) => {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(p.name);
  const [params, setParams] = useState(pretty(p.params));
  const [checklist, setChecklist] = useState(pretty(p.checklist));
  const pp = parse(params);
  const pc = parse(checklist);
  const save = useMutation({
    mutationFn: () => protocolApi.saveProgram(p.code, { name: name.trim(), params: pp.ok ? pp.v : null, checklist: pc.ok ? pc.v : null }),
    onSuccess: () => { setOpen(false); toast.success("Сохранена новая версия программы"); void qc.invalidateQueries({ queryKey: protocolKeys.programs }); },
  });
  const valid = name.trim().length > 0 && pp.ok && pc.ok;
  return (
    <>
      <Button size="sm" variant="secondary" onClick={() => { setName(p.name); setParams(pretty(p.params)); setChecklist(pretty(p.checklist)); setOpen(true); }}><PencilLine />Изменить</Button>
      <Dialog open={open} onClose={() => setOpen(false)} title={`Программа ${p.code}`} description="Новая версия применится к следующим проверкам; старые протоколы не меняются"
        footer={<Button disabled={!valid || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Сохраняем…" : "Сохранить версию"}</Button>}>
        <div className="flex flex-col gap-4">
          <Field label="Название"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label="Параметры (JSON)" error={pp.ok ? null : "Некорректный JSON"}><Textarea rows={6} className="font-mono text-sm" value={params} onChange={(e) => setParams(e.target.value)} /></Field>
          <Field label="Перечень документов (JSON)" error={pc.ok ? null : "Некорректный JSON"}><Textarea rows={10} className="font-mono text-sm" value={checklist} onChange={(e) => setChecklist(e.target.value)} /></Field>
          {save.isError && <p className="text-sm text-danger">{errorText(save.error)}</p>}
        </div>
      </Dialog>
    </>
  );
};

/** FE-SP: программы субсидий — перечень документов и параметры правил; правит только старший специалист. */
export const ProgramsPage = () => {
  const { roles } = useSession();
  const q = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs });
  const [open, setOpen] = useState<string | null>(null);
  return (
    <>
      <PageHeader title="Программы" sub="Условия программ: какие документы нужны и с какими параметрами работают проверки" />
      {q.isPending ? <RowsSkeleton rows={4} /> : q.isError ? (
        <EmptyState title="Программы не загрузились" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !q.data.items.length ? <Card><EmptyState icon={<ScrollText />} title="Программ нет" className="py-16" /></Card> : (
        <motion.div variants={stagger(0.05)} initial="hidden" animate="show" className="flex flex-col gap-3">
          {q.data.items.map((p) => (
            <motion.div key={p.code} variants={fadeUp}><Card className="overflow-hidden">
              <CardHeader className="flex flex-row flex-wrap items-center gap-2">
                <button type="button" className="group flex min-w-0 flex-1 items-start gap-3 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => setOpen(open === p.code ? null : p.code)} aria-expanded={open === p.code}>
                  <span className="grid size-10 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground transition-colors duration-fast group-hover:text-foreground"><ScrollText className="size-5" /></span>
                  <span className="min-w-0 flex-1">
                  <CardTitle className="flex items-center gap-1">{p.name}<ChevronDown className={cn("size-4 shrink-0 text-subtle-foreground transition-transform duration-base", open === p.code && "rotate-180")} /></CardTitle>
                  <p className="text-xs text-muted-foreground">{p.code} · версия {p.version} · документов {p.checklist.length}{p.updatedBy ? ` · изменил ${p.updatedBy}` : ""}</p>
                  </span>
                </button>
                {p.demo && <Status tone="info">демо</Status>}
                {can(roles, "programs") && <EditProgram p={p} />}
              </CardHeader>
              <AutoHeight>{open === p.code && (
              <CardContent className="flex flex-col gap-3">
                <ul className="flex flex-col gap-1.5">
                  {p.checklist.map((c) => (
                    <li key={c.item} className="-mx-2 rounded-md px-2 py-1 text-sm transition-colors duration-fast hover:bg-muted">{c.title}{c.condition && <span className="text-muted-foreground"> · {c.condition}</span>}<span className="text-xs text-muted-foreground"> ({c.types.join(", ")})</span></li>
                  ))}
                </ul>
                <pre className="overflow-x-auto rounded-md bg-muted p-3 text-xs">{pretty(p.params)}</pre>
              </CardContent>
              )}</AutoHeight>
            </Card></motion.div>
          ))}
        </motion.div>
      )}
    </>
  );
};
