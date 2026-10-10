import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PencilLine } from "lucide-react";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Dialog, Field, Input, toast } from "@/shared/ui";

/** FE-PK-06: правка извлечённого поля → rules пересчитывает протокол и шлёт package.checked. */
export const CorrectFieldButton = ({ packageId, documentId, field, label, current }: {
  packageId: string; documentId: string; field: string; label: string; current: string;
}) => {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(current);
  const [reason, setReason] = useState("");
  const save = useMutation({
    mutationFn: () => protocolApi.correct(packageId, documentId, field, value.trim(), reason.trim()),
    onSuccess: () => {
      setOpen(false);
      toast.success("Поле исправлено — протокол пересчитывается");
      void qc.invalidateQueries({ queryKey: protocolKeys.latest(packageId) });
      void qc.invalidateQueries({ queryKey: protocolKeys.versions(packageId) });
    },
  });
  const valid = value.trim().length > 0 && reason.trim().length > 0;
  return (
    <>
      <Button size="icon" variant="ghost" aria-label={`Исправить: ${label}`} onClick={() => { setValue(current); setOpen(true); }}><PencilLine /></Button>
      <Dialog open={open} onClose={() => setOpen(false)} title={`Исправить: ${label}`} description="Исправление сохраняется в истории с вашим именем"
        footer={<Button disabled={!valid || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Сохраняем…" : "Сохранить"}</Button>}>
        <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (valid) save.mutate(); }}>
          <Field label="Значение"><Input autoFocus value={value} onChange={(e) => setValue(e.target.value)} /></Field>
          <Field label="Причина" hint="Например: «на скане видно 1 250 000,00»"><Input value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
          {save.isError && <p className="text-sm text-danger">{errorText(save.error)}</p>}
        </form>
      </Dialog>
    </>
  );
};
