import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Download, Replace, UploadCloud } from "lucide-react";
import { reviewApi, reviewKeys, type DocFile, type UploadRejection } from "@/entities/application";
import { DOCUMENT_ACCEPT, splitDocumentFiles, UploadFeedback } from "@/entities/document";
import { protocolKeys } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { Button, Spinner, toast } from "@/shared/ui";
import { cn } from "@/shared/lib";

const useRefresh = (id: string) => {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: reviewKeys.card(id) });
    void qc.invalidateQueries({ queryKey: reviewKeys.queueAll });
    void qc.invalidateQueries({ queryKey: reviewKeys.history(id) });
    void qc.invalidateQueries({ queryKey: protocolKeys.latest(id) });
    void qc.invalidateQueries({ queryKey: protocolKeys.versions(id) });
  };
};
const Failure = ({ error }: { error: unknown }) => error ? <p role="alert" className="text-sm text-danger">{errorText(error)}{error instanceof ApiError && error.hint ? ` — ${error.hint}` : ""}</p> : null;

export const UploadDocuments = ({ packageId }: { packageId: string }) => {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const [rejected, setRejected] = useState<UploadRejection[]>([]);
  const refresh = useRefresh(packageId);
  const upload = useMutation({ mutationFn: (files: File[]) => reviewApi.upload(packageId, files), onSuccess: (r) => { setRejected((local) => [...local, ...(r.rejected ?? [])]); refresh(); } });
  const send = (files: File[]) => {
    if (!files.length || upload.isPending) return;
    upload.reset(); const selected = splitDocumentFiles(files); setRejected(selected.rejected);
    if (selected.accepted.length) upload.mutate(selected.accepted);
  };
  return <section id="upload-documents" aria-label="Дозагрузка документов" className="mb-4 flex flex-col gap-2">
    <div onDragOver={(e) => { e.preventDefault(); if (!upload.isPending) setOver(true); }} onDragLeave={() => setOver(false)} onDrop={(e) => { e.preventDefault(); setOver(false); send(Array.from(e.dataTransfer.files)); }}
      className={cn("flex flex-col items-center gap-3 rounded-lg border-2 border-dashed border-border-strong bg-surface p-5 text-center", over && "border-primary bg-accent")}>
      {upload.isPending ? <Spinner className="size-6" /> : <UploadCloud className="size-6 text-muted-foreground" />}
      <p className="text-sm text-muted-foreground">Перетащите документы сюда. PDF, DOC/DOCX, XLS/XLSX, JPG/PNG. Можно выбрать несколько файлов.</p>
      <Button variant="secondary" size="sm" disabled={upload.isPending} onClick={() => input.current?.click()}>Выбрать документы</Button>
      <input aria-label="Документы для загрузки" ref={input} type="file" multiple accept={DOCUMENT_ACCEPT} hidden disabled={upload.isPending} onChange={(e) => { send(Array.from(e.target.files ?? [])); e.target.value = ""; }} />
    </div>
    <Failure error={upload.error} /><UploadFeedback rejected={rejected} result={upload.data} />
  </section>;
};
export const FileRecoveryActions = ({ packageId, file, canAct, canDownload }: { packageId: string; file: DocFile; canAct: boolean; canDownload: boolean }) => {
  const input = useRef<HTMLInputElement>(null);
  const [rejected, setRejected] = useState<UploadRejection[]>([]);
  const refresh = useRefresh(packageId);
  const replace = useMutation({ mutationFn: (f: File) => reviewApi.replace(packageId, file.id, f), onSuccess: (r) => { setRejected(r.rejected ?? []); if (r.files.length) { refresh(); if (r.files.some((f) => !f.duplicateOf)) toast.success("Загружена новая версия"); } } });
  const original = useMutation({ mutationFn: () => reviewApi.downloadOriginal(file.id, file.version ?? 1, file.name ?? "document"), onError: (e) => toast.error(errorText(e)) });
  const busy = replace.isPending || original.isPending;
  return <div className="flex flex-col gap-2">
    <div className="flex flex-wrap gap-2">
      {canDownload && <Button size="sm" variant="secondary" disabled={busy} onClick={() => original.mutate()}><Download />{original.isPending ? "Скачиваем…" : "Оригинал"}</Button>}
      {canAct && <>
        <input aria-label="Документ для замены" ref={input} type="file" accept={DOCUMENT_ACCEPT} hidden disabled={busy} onChange={(e) => { const file = e.target.files?.[0]; e.target.value = ""; if (!file || busy) return; replace.reset(); const selected = splitDocumentFiles([file]); setRejected(selected.rejected); if (selected.accepted.length) replace.mutate(file); }} />
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => input.current?.click()}><Replace />Заменить файл</Button>
        <Button size="sm" variant="quiet" onClick={() => { const area = document.getElementById("upload-documents"); area?.scrollIntoView({ behavior: "smooth", block: "center" }); area?.querySelector("button")?.focus({ preventScroll: true }); }}>Дозагрузить документы</Button>
      </>}
    </div>
    <Failure error={replace.error} /><UploadFeedback rejected={rejected} result={replace.data} />
  </div>;
};
