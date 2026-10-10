import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UploadCloud } from "lucide-react";
import { packageApi, packageKeys, type Rejected } from "@/entities/package";
import { errorText } from "@/shared/api";
import { Button, Spinner } from "@/shared/ui";
import { cn } from "@/shared/lib";

const ACCEPT = ".pdf,.jpg,.jpeg,.png,.tif,.tiff,.heic,.docx,.xlsx,.zip";

/** FE-PK-02: файлы и ZIP перетаскиванием или выбором; отказ по файлу — строкой под зоной, а не toast. */
export const UploadDropzone = ({ packageId, disabled }: { packageId: string; disabled?: boolean }) => {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const [rejected, setRejected] = useState<Rejected[]>([]);
  const qc = useQueryClient();
  const upload = useMutation({
    mutationFn: (files: File[]) => packageApi.upload(packageId, files),
    onSuccess: (r) => { setRejected(r.rejected ?? []); void qc.invalidateQueries({ queryKey: packageKeys.card(packageId) }); },
  });
  const send = (list: FileList | null) => { if (list?.length && !upload.isPending) upload.mutate(Array.from(list)); };
  return (
    <div className="flex flex-col gap-2">
      <div
        onDragOver={(e) => { e.preventDefault(); if (!disabled) setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); if (!disabled) send(e.dataTransfer.files); }}
        className={cn("flex flex-col items-center gap-3 rounded-lg border-2 border-dashed border-border-strong bg-surface p-6 text-center transition-colors duration-fast",
          over && "border-primary bg-accent", disabled && "opacity-60")}
      >
        {upload.isPending ? <Spinner className="size-6" /> : <UploadCloud className="size-7 text-muted-foreground" />}
        <div className="text-sm text-muted-foreground">Перетащите файлы или ZIP-архив сюда. PDF, JPG, PNG, TIFF, HEIC, DOCX, XLSX</div>
        <Button variant="secondary" size="sm" disabled={disabled || upload.isPending} onClick={() => input.current?.click()}>Выбрать файлы</Button>
        <input ref={input} type="file" multiple accept={ACCEPT} className="hidden" onChange={(e) => { send(e.target.files); e.target.value = ""; }} />
      </div>
      {upload.isError && <p className="text-sm text-danger">{errorText(upload.error)}</p>}
      {rejected.map((r) => (
        <div key={r.name + r.code} className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground">
          <span className="font-medium">{r.name}</span>: {r.detail}{r.hint ? ` — ${r.hint}` : ""}
        </div>
      ))}
    </div>
  );
};
