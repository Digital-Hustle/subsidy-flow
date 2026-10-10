import { CalendarClock, FileText } from "lucide-react";
import { dateRuFull } from "@/entities/application";
import { typeTitle } from "@/entities/document";
import { Card, CardContent, CardHeader, CardTitle, Status } from "@/shared/ui";
import type { ProgramForm } from "../model/form";

const KIND_LABEL = { REQUIRED: "обязательно", CONDITIONAL: "по условию", ONE_OF: "один из" } as const;

/** Как программу увидит заявитель в кабинете: название, описание, срок и перечень документов. */
export const ProgramPreview = ({ form }: { form: ProgramForm }) => (
  <Card className="bg-muted">
    <CardHeader className="flex-col items-start gap-1">
      <p className="text-xs text-muted-foreground">Так увидит заявитель</p>
      <CardTitle>{form.name.trim() || "Название программы"}</CardTitle>
      {form.description.trim() && <p className="whitespace-pre-line text-sm text-muted-foreground">{form.description.trim()}</p>}
      {form.deadline && <p className="flex items-center gap-1.5 text-sm"><CalendarClock className="size-4 text-muted-foreground" />Приём заявок до {dateRuFull(form.deadline)}</p>}
    </CardHeader>
    <CardContent>
      {!form.checklist.length ? <p className="text-sm text-muted-foreground">Документы ещё не добавлены</p> : (
        <ul className="flex flex-col gap-2">
          {form.checklist.map((r) => (
            <li key={r.key} className="flex items-start gap-2 text-sm">
              <FileText className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1">
                <span className="font-medium">{r.title.trim() || (r.types.length ? r.types.map(typeTitle).join(" или ") : "Документ")}</span>
                {r.kind === "ONE_OF" && r.types.length > 1 && <span className="text-muted-foreground"> — подойдёт: {r.types.map(typeTitle).join(", ")}</span>}
                {r.hint.trim() && <span className="block text-xs text-muted-foreground">{r.hint.trim()}</span>}
              </span>
              <Status tone={r.kind === "REQUIRED" ? "info" : "neutral"}>{KIND_LABEL[r.kind]}</Status>
            </li>
          ))}
        </ul>
      )}
    </CardContent>
  </Card>
);