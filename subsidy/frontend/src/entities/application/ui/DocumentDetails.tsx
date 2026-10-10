import { Status } from "@/shared/ui";
import { documentParties, presenceText, signatureIndicators } from "../model/document-details";
import { fieldText } from "../model/fields";
import type { DocumentCard, ElectronicSignature, FieldCard } from "../model/types";

const Evidence = ({ field }: { field?: FieldCard }) => field ? <>
  {field.raw && <p className="break-words text-xs text-muted-foreground">На документе: {field.raw}</p>}
  <p className="text-xs text-muted-foreground">{field.page ? `Страница ${field.page}. ` : ""}{field.confidence != null ? `Уверенность: ${Math.round(field.confidence * 100)}%` : "Уверенность не указана"}</p>
  {field.checks?.filter((c) => c.status !== "PASSED" && c.status !== "OK").map((c) => <Status key={c.code} tone={c.status === "FAILED" ? "danger" : "warning"}>{c.message}</Status>)}
</> : null;

export const DocumentDetails = ({ doc, electronicSignature }: { doc: DocumentCard; electronicSignature?: ElectronicSignature | null }) => <>
  <section className="flex flex-col gap-3" aria-label="Извлечённые стороны">
    <h4 className="font-semibold">Извлечённые стороны</h4>
    <dl className="grid gap-3 sm:grid-cols-2">
      {documentParties(doc).map(({ label, field }) => <div key={label} className="min-w-0 rounded-md bg-surface p-3">
        <dt className="text-xs text-muted-foreground">{label}</dt><dd className="break-words text-sm">{field?.value == null || field.value === "" ? "Не определено" : fieldText(field.value)}</dd><Evidence field={field} />
      </div>)}
    </dl>
  </section>
  <section className="flex flex-col gap-3" aria-label="Признаки подписей и печати">
    <h4 className="font-semibold">Признаки подписей и печати</h4>
    <dl className="flex flex-col gap-2">
      {signatureIndicators(doc, electronicSignature).map(({ label, value, field, reason }) => <div key={label} className="rounded-md border border-border p-3">
        <div className="flex flex-wrap items-center justify-between gap-2"><dt className="text-sm">{label}</dt><dd><Status tone={typeof value === "boolean" ? "info" : "neutral"}>{presenceText(value)}</Status></dd></div>
        <Evidence field={field} />
        {reason && <p className="mt-1 text-xs text-muted-foreground">{reason === "NOT_INSPECTED" ? "Файл ещё не проверен на наличие электронной подписи" : reason === "PDF_UNREADABLE" ? "Структуру подписи не удалось прочитать" : `Причина: ${reason}`}</p>}
      </div>)}
    </dl>
    <p className="text-xs text-muted-foreground">Наличие электронной подписи определяется по структуре файла. Криптографическая действительность здесь не проверяется.</p>
  </section>
</>;
