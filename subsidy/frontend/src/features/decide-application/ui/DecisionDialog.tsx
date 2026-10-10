import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Undo2, XCircle } from "lucide-react";
import { reviewApi, reviewKeys } from "@/entities/application";
import { Button, Dialog, Field, Segmented, Textarea, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

type D = "APPROVE" | "REJECT" | "RETURN";
const TEXT: Record<D, { tab: string; button: string; done: string; variant: "primary" | "danger" | "secondary" }> = {
  APPROVE: { tab: "Одобрить", button: "Одобрить заявку", done: "Заявка одобрена", variant: "primary" },
  RETURN: { tab: "Вернуть", button: "Вернуть на доработку", done: "Заявка возвращена на доработку", variant: "secondary" },
  REJECT: { tab: "Отказать", button: "Отказать", done: "По заявке принят отказ", variant: "danger" },
};

/** Решение по заявке (только старший, только при наличии протокола). Отказ и возврат — с комментарием, его увидит заявитель. */
export const DecisionDialog = ({ packageId, open, onClose }: { packageId: string; open: boolean; onClose: () => void }) => {
  const qc = useQueryClient();
  const [d, setD] = useState<D>("APPROVE");
  const [comment, setComment] = useState("");
  const m = useMutation({
    mutationFn: () => reviewApi.decide(packageId, d, comment.trim() || undefined),
    onSuccess: () => {
      toast.success(TEXT[d].done);
      void qc.invalidateQueries({ queryKey: reviewKeys.card(packageId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.history(packageId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.queueAll });
      onClose();
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const needComment = d !== "APPROVE" && !comment.trim();
  const Icon = d === "APPROVE" ? CheckCircle2 : d === "RETURN" ? Undo2 : XCircle;
  return (
    <Dialog open={open} onClose={onClose} title="Решение по заявке" description="Решение фиксируется в истории; заявитель увидит его и комментарий в кабинете"
      footer={<Button block size="lg" variant={TEXT[d].variant} disabled={needComment || m.isPending} onClick={() => m.mutate()}>
        <Icon />{m.isPending ? "Сохраняем…" : TEXT[d].button}</Button>}>
      <div className="flex flex-col gap-4">
        <Segmented block value={d} onChange={setD} label="Решение" options={(Object.keys(TEXT) as D[]).map((k) => ({ value: k, label: TEXT[k].tab }))} />
        <Field label={d === "APPROVE" ? "Комментарий" : d === "RETURN" ? "Что исправить (обязательно)" : "Причина отказа (обязательно)"}>
          <Textarea value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} rows={4} />
        </Field>
      </div>
    </Dialog>
  );
};
