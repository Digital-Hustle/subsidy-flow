import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { reviewApi, reviewKeys, type DocumentCard } from "@/entities/application";
import { typeTitle } from "@/entities/document";
import { Button, Dialog, Field, Select, Textarea, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

const PRESETS = ["Загрузите скан лучшего качества: текст не читается", "Документ без подписи или печати", "Загрузите действующий документ — срок истёк", "Не хватает страниц", "Суммы в заявлении и обосновании затрат не совпадают"];

/** Запросить у заявителя документ или исправление: статус NEEDS_FIX, письмо на контакт, запрос появится в кабинете. */
export const RequestDialog = ({ packageId, docs, missing, open, onClose, onClosed }: {
  packageId: string; docs: DocumentCard[]; missing: { type: string; title: string }[]; open: boolean; onClose: () => void; onClosed?: () => void;
}) => {
  const qc = useQueryClient();
  const options = [
    ...missing.map((t) => ({ value: `t:${t.type}`, label: `Загрузить: ${t.title}` })),
    ...docs.filter((d) => d.real).map((d) => ({ value: `d:${d.id}`, label: `Исправить: ${d.typeName ?? typeTitle(d.type)}${d.fileName ? ` (${d.fileName})` : ""}` })),
    { value: "g:", label: "Общий комментарий к заявке" },
  ];
  const [target, setTarget] = useState<string>(options[0]?.value ?? "");
  const [reason, setReason] = useState("");
  const m = useMutation({
    mutationFn: () => reviewApi.request(packageId, [target.startsWith("d:") ? { documentId: target.slice(2), reason: reason.trim() } : target.startsWith("t:") ? { type: target.slice(2), reason: reason.trim() } : { reason: reason.trim() }]),
    onSuccess: () => {
      toast.success("Запрос отправлен заявителю");
      void qc.invalidateQueries({ queryKey: reviewKeys.card(packageId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.history(packageId) });
      onClose();
    },
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title="Запросить у заявителя" description="Текст увидит заявитель в кабинете и в письме — пишите, что сделать"
      footer={<Button block size="lg" disabled={!target || !reason.trim() || m.isPending} onClick={() => m.mutate()}><Send />{m.isPending ? "Отправляем…" : "Отправить запрос"}</Button>}>
      <div className="flex flex-col gap-4">
        <Field label="Что нужно"><Select value={target} onChange={setTarget} options={options} /></Field>
        <Field label="Сообщение"><Textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} rows={3} /></Field>
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p) => <Button key={p} size="sm" variant="secondary" onClick={() => setReason(p)}>{p}</Button>)}
        </div>
      </div>
    </Dialog>
  );
};
