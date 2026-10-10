import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CalendarClock, ChevronDown, History, ScrollText } from "lucide-react";
import { dateRuFull, dateTimeRu } from "@/entities/application";
import { protocolApi, protocolKeys, ruleTitle, type Program } from "@/entities/protocol";
import { typeTitle } from "@/entities/document";
import { can, useSession } from "@/entities/session";
import { CreateProgramButton, EditProgramButton } from "@/features/edit-program";
import { errorText } from "@/shared/api";
import { AutoHeight, Button, Card, CardContent, CardHeader, CardTitle, EmptyState, PageHeader, RowsSkeleton, Status } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { fadeUp, stagger } from "@/shared/config/motion";

const KIND_LABEL: Record<string, string> = { REQUIRED: "обязательно", CONDITIONAL: "по условию", ONE_OF: "один из" };

/** История версий программы (rules /programs/{code}/versions), новые первыми. */
const ProgramVersions = ({ code }: { code: string }) => {
  const q = useQuery({ queryKey: protocolKeys.programVersions(code), queryFn: () => protocolApi.programVersions(code) });
  if (q.isPending) return <RowsSkeleton rows={2} />;
  if (q.isError) return <p className="text-sm text-muted-foreground">История не загрузилась: {errorText(q.error)} <Button size="sm" variant="quiet" onClick={() => q.refetch()}>Повторить</Button></p>;
  return (
    <ol className="flex flex-col gap-1">
      {q.data.map((v) => (
        <li key={v.version} className="flex flex-wrap gap-x-2 text-sm"><span className="font-medium">Версия {v.version}</span><span className="text-muted-foreground">{v.name}</span><span className="text-xs text-subtle-foreground">{v.updatedAt ? dateTimeRu(v.updatedAt) : ""}{v.updatedBy ? ` · ${v.updatedBy}` : ""}</span></li>
      ))}
    </ol>
  );
};

const ProgramDetails = ({ p }: { p: Program }) => {
  const [history, setHistory] = useState(false);
  const rules = p.params?.rules ?? [];
  return (
    <CardContent className="flex flex-col gap-4 border-t border-border">
      {p.params?.description && <p className="whitespace-pre-line text-sm">{p.params.description}</p>}
      <div className="flex flex-col gap-1.5">
        <h3 className="text-xs font-medium uppercase text-muted-foreground">Документы</h3>
        <ul className="flex flex-col gap-1.5">
          {(p.checklist ?? []).map((c) => (
            <li key={c.item} className="-mx-2 flex flex-wrap items-center gap-2 rounded-md px-2 py-1 text-sm transition-colors duration-fast hover:bg-muted">
              <span>{c.title ?? c.item}</span>
              <span className="text-xs text-muted-foreground">({(c.types ?? []).map(typeTitle).join(", ")})</span>
              {c.conditionText && <span className="text-xs text-muted-foreground">· {c.conditionText}</span>}
              <Status className="ml-auto" tone={c.kind === "REQUIRED" ? "info" : "neutral"}>{KIND_LABEL[c.kind ?? "REQUIRED"] ?? c.kind}</Status>
            </li>
          ))}
        </ul>
      </div>
      {!!rules.length && (
        <div className="flex flex-col gap-1.5">
          <h3 className="text-xs font-medium uppercase text-muted-foreground">Проверки</h3>
          <ul className="flex flex-wrap gap-1.5">
            {rules.map((r) => <li key={r.code}><Status tone={r.enabled === false ? "neutral" : r.severity === "WARNING" ? "warning" : "info"}>{r.title ?? ruleTitle(r.code)}{r.enabled === false ? " — выкл." : ""}</Status></li>)}
          </ul>
        </div>
      )}
      <div className="flex flex-col gap-2">
        <Button size="sm" variant="quiet" className="self-start" onClick={() => setHistory((h) => !h)} aria-expanded={history}><History />История версий</Button>
        <AutoHeight>{history && <ProgramVersions code={p.code} />}</AutoHeight>
      </div>
    </CardContent>
  );
};

/** Программы субсидий: документы, проверки, срок приёма; создаёт и меняет только старший специалист (новая версия). */
export const ProgramsPage = () => {
  const { roles } = useSession();
  const q = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs });
  const [open, setOpen] = useState<string | null>(null);
  const editor = can(roles, "programs");
  const programs = q.data ?? [];
  return (
    <>
      <PageHeader title="Программы" sub="Условия программ: какие документы нужны и с какими параметрами работают проверки"
        actions={editor && q.isSuccess ? <CreateProgramButton programs={programs} /> : undefined} />
      {q.isPending ? <Card className="overflow-hidden"><RowsSkeleton rows={4} /></Card> : q.isError ? (
        <EmptyState title="Программы не загрузились" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !programs.length ? <Card><EmptyState icon={<ScrollText />} title="Программ нет" text={editor ? "Создайте первую программу — кнопка «Новая программа»" : undefined} className="py-16" /></Card> : (
        <motion.div variants={stagger(0.05)} initial="hidden" animate="show" className="flex flex-col gap-3">
          {programs.map((p) => (
            <motion.div key={p.code} variants={fadeUp} layout><Card className="overflow-hidden">
              <CardHeader className="flex flex-row flex-wrap items-center gap-3 pb-4 sm:pb-5">
                <button type="button" className="group flex min-w-0 flex-1 items-center gap-3 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => setOpen(open === p.code ? null : p.code)} aria-expanded={open === p.code}>
                  <span className="grid size-10 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground transition-colors duration-fast group-hover:text-foreground"><ScrollText className="size-5" /></span>
                  <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <CardTitle className="flex items-center gap-1">{p.name}<ChevronDown className={cn("size-4 shrink-0 text-subtle-foreground transition-transform duration-base", open === p.code && "rotate-180")} /></CardTitle>
                    <p className="text-xs text-muted-foreground">{p.code} · версия {p.version} · документов {p.checklist?.length ?? 0}{p.updatedBy && !p.updatedBy.startsWith("migration") ? ` · изменил ${p.updatedBy}` : ""}</p>
                    {p.params?.deadline && <p className="flex items-center gap-1 text-xs text-muted-foreground"><CalendarClock className="size-3.5" />приём до {dateRuFull(p.params.deadline)}</p>}
                  </span>
                </button>
                {p.accepting === false && <Status tone="warning">приём закрыт</Status>}
                {p.demo && <Status tone="info">демо</Status>}
                {editor && <EditProgramButton program={p} programs={programs} />}
              </CardHeader>
              <AutoHeight>{open === p.code && <ProgramDetails p={p} />}</AutoHeight>
            </Card></motion.div>
          ))}
        </motion.div>
      )}
    </>
  );
};
