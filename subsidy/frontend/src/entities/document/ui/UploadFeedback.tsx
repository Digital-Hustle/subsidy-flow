import type { DocumentRejection } from "../model/upload";
type UploadResult = { files: { duplicateOf?: string | null }[] };

/** Отказ одного файла не отменяет успешный приём остальных. */
export const UploadFeedback = ({ rejected, result }: { rejected: DocumentRejection[]; result?: UploadResult }) => (
  <div className="flex flex-col gap-2" aria-live="polite">
    {result && result.files.length > 0 && <p className="text-sm text-success">Принято файлов: {result.files.length}. {result.files.some((f) => f.duplicateOf) ? "В том числе уже загруженные файлы." : "Обработка продолжится в фоне."}</p>}
    {result && !result.files.length && !rejected.length && <p className="text-sm text-muted-foreground">Сервер не подтвердил приём файла. Повторите загрузку.</p>}
    {rejected.map((r, i) => (
      <div key={`${r.name}-${r.code}-${i}`} className="break-words rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground">
        <span className="font-medium">{r.name}</span>: {r.detail}{r.hint ? ` — ${r.hint}` : ""}
      </div>
    ))}
  </div>
);
