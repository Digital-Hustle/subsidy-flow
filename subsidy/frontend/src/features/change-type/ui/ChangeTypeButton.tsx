import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Tags } from "lucide-react";
import { packageApi, packageKeys } from "@/entities/package";
import { errorText } from "@/shared/api";
import { Button, Dialog, Field, Input, Select, toast } from "@/shared/ui";

/** FE-PK-06: ручная смена вида документа → docproc переизвлекает поля, протокол пересчитывается. */
export const ChangeTypeButton = ({ packageId, documentId, current }: { packageId: string; documentId: string; current: string | null }) => {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState(current ?? "");
  const [reason, setReason] = useState("");
  const types = useQuery({ queryKey: packageKeys.types, queryFn: packageApi.types, enabled: open, staleTime: Infinity });
  const options = (types.data?.items ?? []).map((t) => ({ value: t.code, label: t.name, hint: t.code }));
  const save = useMutation({
    mutationFn: () => packageApi.retype(documentId, type, reason.trim()),
    onSuccess: () => {
      setOpen(false);
      toast.success("Вид изменён — документ разбирается заново");
      void qc.invalidateQueries({ queryKey: packageKeys.documents(packageId) });
      void qc.invalidateQueries({ queryKey: packageKeys.card(packageId) });
    },
  });
  const valid = !!type && type !== current && reason.trim().length > 0;
  return (
    <>
      <Button size="sm" variant="ghost" onClick={() => { setType(current ?? ""); setOpen(true); }}><Tags />Сменить вид</Button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Вид документа" description="Если классификатор ошибся — выберите вид вручную"
        footer={<Button disabled={!valid || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Сохраняем…" : "Сохранить"}</Button>}>
        <div className="flex flex-col gap-4">
          <Field label="Вид"><Select aria-label="Вид документа" value={type} onChange={setType} options={options} placeholder={types.isPending ? "Загружаем…" : "Выберите"} /></Field>
          <Field label="Причина"><Input value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
          {save.isError && <p className="text-sm text-danger">{errorText(save.error)}</p>}
        </div>
      </Dialog>
    </>
  );
};
