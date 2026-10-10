import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Gavel } from "lucide-react";
import { packageApi, packageKeys, type Decision } from "@/entities/package";
import { errorText } from "@/shared/api";
import { Button, Dialog, Field, Segmented, Textarea, toast } from "@/shared/ui";

const OPTIONS: { value: Decision; label: string }[] = [
  { value: "APPROVE", label: "Одобрить" },
  { value: "RETURN", label: "Вернуть на доработку" },
  { value: "REJECT", label: "Отказать" },
];

/** FE-SP-04: решение по пакету (только SENIOR). Для отказа и возврата комментарий обязателен — проверяет и сервер. */
export const DecideButton = ({ packageId }: { packageId: string }) => {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [decision, setDecision] = useState<Decision>("APPROVE");
  const [comment, setComment] = useState("");
  const needComment = decision !== "APPROVE";
  const valid = !needComment || comment.trim().length > 0;
  const decide = useMutation({
    mutationFn: () => packageApi.decide(packageId, decision, comment.trim()),
    onSuccess: () => {
      setOpen(false);
      toast.success("Решение сохранено");
      void qc.invalidateQueries({ queryKey: packageKeys.card(packageId) });
      void qc.invalidateQueries({ queryKey: packageKeys.queueAll });
    },
  });
  return (
    <>
      <Button onClick={() => setOpen(true)}><Gavel />Решение</Button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Решение по пакету" description="Решение принимает человек: протокол — только основание"
        footer={<Button disabled={!valid || decide.isPending} onClick={() => decide.mutate()}>{decide.isPending ? "Сохраняем…" : "Сохранить решение"}</Button>}>
        <div className="flex flex-col gap-4">
          <Segmented label="Решение" value={decision} onChange={setDecision} options={OPTIONS} block />
          <Field label={needComment ? "Комментарий (обязательно)" : "Комментарий"} hint={needComment ? "Организация увидит причину в письме" : undefined}>
            <Textarea rows={4} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
          </Field>
          {decide.isError && <p className="text-sm text-danger">{errorText(decide.error)}</p>}
        </div>
      </Dialog>
    </>
  );
};
