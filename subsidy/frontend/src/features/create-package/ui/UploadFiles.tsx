import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Upload } from "lucide-react";
import { reviewApi, reviewKeys } from "@/entities/application";
import { errorText } from "@/shared/api";
import { Button } from "@/shared/ui";

type Rejected = { name: string; code?: string; detail?: string; hint?: string };

/** Специалист добавляет файлы в пакет. Отказ по файлу — строкой под кнопкой, причину и подсказку даёт сервер. */
export const UploadFiles = ({ packageId, label = "Загрузить файлы" }: { packageId: string; label?: string }) => {
  const input = useRef<HTMLInputElement>(null);
  const [rejected, setRejected] = useState<Rejected[]>([]);
  const qc = useQueryClient();
  const up = useMutation({
    mutationFn: (files: File[]) => reviewApi.uploadFiles(packageId, files),
    onSuccess: (r) => { setRejected(r?.rejected ?? []); void qc.invalidateQueries({ queryKey: reviewKeys.card(packageId) }); },
  });
  return (
    <div className="flex flex-col gap-2">
      <Button variant="secondary" disabled={up.isPending} onClick={() => input.current?.click()}><Upload />{up.isPending ? "Загружаем…" : label}</Button>
      <input ref={input} type="file" multiple accept="application/pdf,.pdf" hidden onChange={(e) => { const l = e.target.files; if (l?.length) up.mutate(Array.from(l)); e.target.value = ""; }} />
      {up.isError && <p className="text-sm text-danger">{errorText(up.error)}</p>}
      {rejected.map((r) => <p key={r.name + (r.code ?? "")} className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground"><b className="font-medium">{r.name}</b>: {r.detail}{r.hint ? ` — ${r.hint}` : ""}</p>)}
    </div>
  );
};
