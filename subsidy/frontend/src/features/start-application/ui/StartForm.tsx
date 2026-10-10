import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowRight, ShieldCheck } from "lucide-react";
import { applicantApi, applicantKeys, dateRuFull } from "@/entities/application";
import { Button, Field, Input, Select, Skeleton, SwitchRow, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { applicationKey, parseContact } from "@/shared/lib";
import { routes } from "@/shared/const/router";

/** Программа → контакт → согласие → заявка. Ключ — во фрагменте ссылки, код возобновления показываем один раз. */
export const StartForm = ({ onCreated }: { onCreated?: (r: { id: string; key: string; resumeCode?: string }) => void }) => {
  const nav = useNavigate();
  const programs = useQuery({ queryKey: applicantKeys.programs, queryFn: applicantApi.programs, staleTime: 5 * 60_000 });
  const [program, setProgram] = useState("");
  const [consent, setConsent] = useState(false);
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [touched, setTouched] = useState(false);
  const list = programs.data ?? [];
  const code = program || (list.length === 1 ? list[0].code : "");
  const chosen = list.find((p) => p.code === code);
  const pe = parseContact(email);
  const emailOk = !!pe && "email" in pe;
  const pp = phone.trim() ? parseContact(phone) : null;
  const phoneOk = !phone.trim() || (!!pp && "phone" in pp);
  const emailErr = touched && !emailOk ? (email.trim() ? "Проверьте адрес почты — например, name@company.ru" : "Укажите почту — на неё придут уведомления по заявке") : null;
  const phoneErr = touched && !phoneOk ? "Проверьте номер — например, +7 900 000-00-00" : null;
  const m = useMutation({
    mutationFn: () => applicantApi.create(code, { email: emailOk ? (pe as { email: string }).email : email.trim(), ...(pp && "phone" in pp ? { phone: pp.phone } : {}) }),
    onSuccess: (r) => {
      if (!r.applicationId || !r.accessKey) return;
      applicationKey.set(r.applicationId, r.accessKey);
      onCreated?.({ id: r.applicationId, key: r.accessKey, resumeCode: r.resumeCode });
      nav(routes.applyApplication(r.applicationId), { state: { resumeCode: r.resumeCode, fresh: true } });
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const ready = !!code && consent && emailOk && phoneOk;
  return (
    <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); setTouched(true); if (ready && !m.isPending) m.mutate(); }}>
      <Field label="Программа поддержки" hint={chosen?.description ?? (chosen?.deadline ? `Приём заявок до ${dateRuFull(chosen.deadline)}` : undefined)}
        error={programs.isError ? "Не удалось загрузить программы — обновите страницу" : null}>
        {programs.isPending ? <Skeleton className="h-control-lg rounded-md" /> : !list.length ? (
          <Input disabled aria-label="Программа" placeholder={programs.isError ? "Программы недоступны" : "Сейчас нет открытых программ"} />
        ) : (
          <Select aria-label="Программа" value={code} onChange={setProgram} placeholder="Выберите программу"
            options={list.map((p) => ({ value: p.code, label: p.name ?? p.code, hint: p.deadline ? `до ${dateRuFull(p.deadline)}` : undefined }))} />
        )}
      </Field>
      <Field label="Рабочая почта" hint="Пришлём ссылку на заявку и сообщения специалиста" error={emailErr}>
        <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} onBlur={() => email.trim() && setTouched(true)} aria-invalid={!!emailErr} placeholder="name@company.ru" autoComplete="email" inputMode="email" maxLength={120} />
      </Field>
      <Field label="Телефон (по желанию)" error={phoneErr}>
        <Input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} aria-invalid={!!phoneErr} placeholder="+7 900 000-00-00" autoComplete="tel" inputMode="tel" maxLength={20} />
      </Field>
      <SwitchRow icon={<ShieldCheck />} title="Согласен на обработку персональных данных"
        text="Документы нужны только для рассмотрения заявки на субсидию. Их видит специалист агентства" checked={consent} onChange={setConsent} />
      <Button type="submit" size="lg" block disabled={!consent || !code || m.isPending}>{m.isPending ? "Создаём заявку…" : "Начать"}<ArrowRight /></Button>
    </form>
  );
};
