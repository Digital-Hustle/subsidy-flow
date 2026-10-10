import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { RefreshCw, Replace, Trash2 } from "lucide-react";
import { packageApi, packageKeys } from "@/entities/package";
import { errorText } from "@/shared/api";
import { Button, toast } from "@/shared/ui";

/** FE-PK-07: новая версия файла, удаление, повторный разбор (SENIOR). Документы файла пересобираются на сервере. */
export const FileActions = ({ packageId, fileId, name, canEdit, canReprocess }: { packageId: string; fileId: string; name: string; canEdit: boolean; canReprocess: boolean }) => {
  const qc = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: packageKeys.card(packageId) });
    void qc.invalidateQueries({ queryKey: packageKeys.documents(packageId) });
  };
  const onError = (e: unknown) => toast.error(errorText(e));
  const replace = useMutation({ mutationFn: (f: File) => packageApi.replace(packageId, fileId, f), onSuccess: () => { toast.success("Загружена новая версия"); refresh(); }, onError });
  const remove = useMutation({ mutationFn: () => packageApi.remove(packageId, fileId), onSuccess: () => { toast.success(`Файл «${name}» удалён`); refresh(); }, onError });
  const reprocess = useMutation({ mutationFn: () => packageApi.reprocess(fileId), onSuccess: () => { toast.info("Файл отправлен на повторный разбор"); refresh(); }, onError });
  const busy = replace.isPending || remove.isPending || reprocess.isPending;
  return (
    <div className="flex gap-1">
      {canEdit && (
        <>
          <input ref={input} type="file" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) replace.mutate(f); e.target.value = ""; }} />
          <Button size="icon" variant="ghost" aria-label="Заменить файл" disabled={busy} onClick={() => input.current?.click()}><Replace /></Button>
          <Button size="icon" variant="ghost" aria-label="Удалить файл" disabled={busy} onClick={() => remove.mutate()}><Trash2 /></Button>
        </>
      )}
      {canReprocess && <Button size="icon" variant="ghost" aria-label="Разобрать заново" disabled={busy} onClick={() => reprocess.mutate()}><RefreshCw /></Button>}
    </div>
  );
};
