import { useEffect, useRef, useState } from "react";
import { Check, X } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import { useCorrectField } from "../model/mutations";

/** Правка значения поля прямо в строке: Enter — сохранить, Esc — отмена. */
export const FieldEditor = ({ packageId, docId, name, value, onDone, onSaved }: {
  packageId: string; docId: string; name: string; value: string; onDone: () => void; onSaved?: (v: string) => void;
}) => {
  const [v, setV] = useState(value);
  const ref = useRef<HTMLInputElement>(null);
  const m = useCorrectField(packageId);
  useEffect(() => { ref.current?.focus(); ref.current?.select(); }, []);
  const save = () => {
    if (v === value) { onDone(); return; }
    const next = v.trim();
    m.mutate({ docId, name, value: next }, { onSuccess: () => { onSaved?.(next); onDone(); } });
  };
  return (
    <form className="flex items-center gap-1.5" onSubmit={(e) => { e.preventDefault(); save(); }}
      onKeyDown={(e) => { if (e.key === "Escape") { e.stopPropagation(); onDone(); } }}>
      <Input ref={ref} value={v} onChange={(e) => setV(e.target.value)} maxLength={500} className="h-control-sm text-sm" aria-label="Новое значение" />
      <Button type="submit" size="icon-sm" aria-label="Сохранить" disabled={m.isPending}><Check /></Button>
      <Button size="icon-sm" variant="quiet" aria-label="Отмена" onClick={onDone}><X /></Button>
    </form>
  );
};
