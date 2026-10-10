import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation } from "@tanstack/react-query";
import { KeyRound } from "lucide-react";
import { applicantApi } from "@/entities/application";
import { Button, Field, Input } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { applicationKey, contactError, parseContact } from "@/shared/lib";
import { routes } from "@/shared/const/router";

/** Вход в заявку по коду возобновления: телефон/почта + код → новый ключ (5 попыток, дальше 429 с сервера). */
export const ResumeForm = () => {
  const nav = useNavigate();
  const [who, setWho] = useState("");
  const [code, setCode] = useState("");
  const [touched, setTouched] = useState(false);
  const parsed = parseContact(who);
  const whoErr = touched && !parsed ? contactError(who) : null;
  const m = useMutation({
    mutationFn: () => applicantApi.resume(parsed && "email" in parsed ? parsed.email : parsed?.phone ?? who.trim(), code.trim()),
    onSuccess: (r) => {
      if (!r.applicationId || !r.accessKey) return;
      applicationKey.set(r.applicationId, r.accessKey);
      nav(routes.applyApplication(r.applicationId), { replace: true });
    },
  });
  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); setTouched(true); if (parsed && code.trim().length >= 4 && !m.isPending) m.mutate(); }}>
      <Field label="Почта или телефон из заявки" error={whoErr}><Input value={who} onChange={(e) => setWho(e.target.value)} onBlur={() => who.trim() && setTouched(true)} aria-invalid={!!whoErr} autoComplete="email" maxLength={120} /></Field>
      <Field label="Код возобновления" error={m.isError ? errorText(m.error) : null}>
        <Input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} autoComplete="one-time-code" maxLength={16} className="font-mono tracking-widest" />
      </Field>
      <Button type="submit" variant="secondary" size="lg" block disabled={!who || !code || m.isPending}><KeyRound />{m.isPending ? "Проверяем…" : "Вернуться к заявке"}</Button>
    </form>
  );
};
