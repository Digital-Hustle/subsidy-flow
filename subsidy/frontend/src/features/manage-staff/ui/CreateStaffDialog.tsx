import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UserPlus } from "lucide-react";
import { STAFF_ROLES, STAFF_ROLE_TITLE, staffApi, staffKeys, type StaffRole } from "@/entities/staff";
import { Button, Dialog, Field, Input, Segmented, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Пригласить сотрудника по почте — пароля нет, код входа придёт письмом. */
export const CreateStaffDialog = ({ open, onClose }: { open: boolean; onClose: () => void }) => {
  const qc = useQueryClient();
  const [email, setEmail] = useState("");
  const [first, setFirst] = useState("");
  const [last, setLast] = useState("");
  const [role, setRole] = useState<StaffRole>("SPECIALIST");
  const l = email.trim().toLowerCase();
  const emailErr = l && !EMAIL.test(l) ? "Проверьте адрес: например, ivanov@company.ru" : null;
  const m = useMutation({
    mutationFn: () => staffApi.invite({ email: l, name: `${last.trim()} ${first.trim()}`.trim(), role }),
    onSuccess: (e) => {
      void qc.invalidateQueries({ queryKey: staffKeys.all });
      toast.success(`Приглашение отправлено на ${e.email}`);
      onClose();
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const reset = () => { setEmail(""); setFirst(""); setLast(""); setRole("SPECIALIST"); };
  const ready = l && !emailErr && first.trim() && last.trim();
  return (
    <Dialog open={open} onClose={onClose} onClosed={reset} title="Пригласить сотрудника" description="Пароля не будет: пришлём приглашение, а входить он будет по коду из письма"
      footer={<Button block size="lg" disabled={!ready || m.isPending} onClick={() => m.mutate()}><UserPlus />{m.isPending ? "Отправляем…" : "Отправить приглашение"}</Button>}>
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (ready) m.mutate(); }}>
        <Segmented block value={role} onChange={setRole} label="Роль" options={STAFF_ROLES.map((r) => ({ value: r, label: STAFF_ROLE_TITLE[r] }))} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Фамилия"><Input value={last} onChange={(e) => setLast(e.target.value)} maxLength={64} autoComplete="off" /></Field>
          <Field label="Имя"><Input value={first} onChange={(e) => setFirst(e.target.value)} maxLength={64} autoComplete="off" /></Field>
        </div>
        <Field label="Рабочая почта" error={emailErr} hint="На неё придут приглашение и коды входа">
          <Input type="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} autoCapitalize="none" spellCheck={false} autoComplete="off" maxLength={254} />
        </Field>
        <button type="submit" hidden />
      </form>
    </Dialog>
  );
};
