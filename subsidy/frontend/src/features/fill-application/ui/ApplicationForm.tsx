import { useState } from "react";
import { ArrowRight } from "lucide-react";
import { ADDRESS_PURPOSE, RIGHT_TYPE, type ApplicationData } from "@/entities/application";
import { Button, DatePicker, Field, Input, Segmented, Switch, Textarea } from "@/shared/ui";
import { innError, ogrnError, todayKey } from "@/shared/lib";

type Form = {
  orgInn: string; orgName: string; ogrn: string; address: string; purpose: string; project: string; amount: string; date: string;
  fullName: string; position: string; authority: string; hasProperty: boolean; cadastral: string; propAddress: string; rightType: string;
};

const digits = (s: string) => s.replace(/\D/g, "");
/** «1 500 000,5» → "1500000.50": деньги уходят строкой с двумя знаками. */
const money = (s: string) => {
  const t = s.replace(/[\s\u00a0₽]/g, "").replace(",", ".");
  if (!/^\d+(\.\d{1,2})?$/.test(t) || Number(t) <= 0) return null;
  const [i, f = ""] = t.split(".");
  return `${i.replace(/^0+(?=\d)/, "")}.${f.padEnd(2, "0")}`;
};
const s = (v?: string | null) => v ?? "";
const or = (v: string) => (v.trim() ? v.trim() : null);

const fromData = (a?: ApplicationData | null): Form => ({
  orgInn: s(a?.orgInn), orgName: s(a?.orgName), ogrn: s(a?.ogrn), address: s(a?.address?.value), purpose: s(a?.address?.purpose) || "LEGAL",
  project: s(a?.project), amount: s(a?.requestedAmount), date: s(a?.applicationDate) || todayKey(),
  fullName: s(a?.signatory?.fullName), position: s(a?.signatory?.position), authority: s(a?.signatory?.authority),
  hasProperty: !!a?.property, cadastral: s(a?.property?.cadastralNumber), propAddress: s(a?.property?.address), rightType: s(a?.property?.rightType) || "LEASE",
});

const toData = (f: Form): ApplicationData => ({
  orgInn: or(digits(f.orgInn)), orgName: or(f.orgName), ogrn: or(digits(f.ogrn)),
  address: { value: or(f.address), purpose: f.purpose || null }, project: or(f.project), requestedAmount: money(f.amount), applicationDate: f.date || null,
  signatory: { fullName: or(f.fullName), position: or(f.position), authority: or(f.authority) },
  property: f.hasProperty ? { cadastralNumber: or(f.cadastral), address: or(f.propAddress), rightType: f.rightType || null } : null,
});

/**
 * Анкета заявки — тот же объект `application`, что у специалиста (B1). Черновик можно сохранить частично,
 * полноту и формат проверяет сервер (на сохранении и при отправке). Подсветка ИНН/ОГРН — только подсказка.
 */
export const ApplicationForm = ({ initial, onSave, saving, submitLabel = "Сохранить и перейти к документам", serverError }: {
  initial?: ApplicationData | null; onSave: (a: ApplicationData) => void; saving?: boolean; submitLabel?: string; serverError?: { field?: string; text: string } | null;
}) => {
  const [f, setF] = useState<Form>(() => fromData(initial));
  const [touched, setTouched] = useState(false);
  const set = <K extends keyof Form>(k: K) => (v: Form[K]) => setF((x) => ({ ...x, [k]: v }));
  const inn = digits(f.orgInn);
  const ogrn = digits(f.ogrn);
  const err = {
    orgInn: innError(inn),
    ogrn: ogrnError(ogrn),
    amount: f.amount && !money(f.amount) ? "Сумма в рублях, например 1 500 000 или 1500000,50" : null,
  };
  const server = (field: string) => (serverError?.field && serverError.field.endsWith(field) ? serverError.text : null);
  const show = (k: keyof typeof err) => (touched || f[k as keyof Form] ? err[k] : null);
  const blocking = !!(err.orgInn || err.ogrn || err.amount);
  return (
    <form className="flex flex-col gap-6" onSubmit={(e) => { e.preventDefault(); setTouched(true); if (!blocking && !saving) onSave(toData(f)); }}>
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-3 text-base font-semibold">Организация</legend>
        <Field label="Полное наименование" error={server("orgName")}><Input value={f.orgName} onChange={(e) => set("orgName")(e.target.value)} placeholder="ООО «Ромашка»" maxLength={300} /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="ИНН" error={show("orgInn") ?? server("orgInn")}><Input value={f.orgInn} onChange={(e) => set("orgInn")(e.target.value)} inputMode="numeric" maxLength={12} placeholder="10 или 12 цифр" aria-invalid={!!show("orgInn")} /></Field>
          <Field label="ОГРН" error={show("ogrn") ?? server("ogrn")}><Input value={f.ogrn} onChange={(e) => set("ogrn")(e.target.value)} inputMode="numeric" maxLength={15} placeholder="13 или 15 цифр" aria-invalid={!!show("ogrn")} /></Field>
        </div>
        <Field label="Адрес" error={server("address")}>
          <Input value={f.address} onChange={(e) => set("address")(e.target.value)} placeholder="Город, улица, дом" maxLength={500} />
        </Field>
        <Segmented block label="Какой это адрес" value={f.purpose} onChange={set("purpose")} options={Object.entries(ADDRESS_PURPOSE).map(([value, label]) => ({ value, label: label.replace(" адрес", "").replace("Место реализации проекта", "Место проекта") }))} />
      </fieldset>
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-3 text-base font-semibold">Проект и сумма</legend>
        <Field label="Проект" hint="Название и коротко — что сделаете на субсидию" error={server("project")}><Textarea value={f.project} onChange={(e) => set("project")(e.target.value)} rows={3} maxLength={2000} /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Запрашиваемая сумма, ₽" error={show("amount") ?? server("requestedAmount")}><Input value={f.amount} onChange={(e) => set("amount")(e.target.value)} inputMode="decimal" placeholder="1 500 000" maxLength={20} aria-invalid={!!show("amount")} /></Field>
          <Field label="Дата заявления" error={server("applicationDate")}><DatePicker aria-label="Дата заявления" value={f.date} onChange={set("date")} max={todayKey()} /></Field>
        </div>
      </fieldset>
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-3 text-base font-semibold">Кто подписывает</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="ФИО" error={server("fullName")}><Input value={f.fullName} onChange={(e) => set("fullName")(e.target.value)} autoComplete="name" maxLength={200} /></Field>
          <Field label="Должность" error={server("position")}><Input value={f.position} onChange={(e) => set("position")(e.target.value)} placeholder="Генеральный директор" maxLength={200} /></Field>
        </div>
        <Field label="Основание полномочий" error={server("authority")}><Input value={f.authority} onChange={(e) => set("authority")(e.target.value)} placeholder="Устав или доверенность № … от …" maxLength={300} /></Field>
      </fieldset>
      <fieldset className="flex flex-col gap-4">
        <label className="flex cursor-pointer items-center justify-between gap-3 text-base font-semibold">Проект связан с помещением или участком<Switch checked={f.hasProperty} onChange={set("hasProperty")} label="Есть помещение" /></label>
        {f.hasProperty && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Кадастровый номер" error={server("cadastralNumber")}><Input value={f.cadastral} onChange={(e) => set("cadastral")(e.target.value)} placeholder="61:44:0050706:12" maxLength={40} /></Field>
              <Field label="Адрес объекта"><Input value={f.propAddress} onChange={(e) => set("propAddress")(e.target.value)} maxLength={500} /></Field>
            </div>
            <Segmented block label="Право на объект" value={f.rightType} onChange={set("rightType")} options={Object.entries(RIGHT_TYPE).map(([value, label]) => ({ value, label: label.replace("Безвозмездное пользование", "Пользование") }))} />
          </>
        )}
      </fieldset>
      {serverError && !serverError.field && <p className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground" role="alert">{serverError.text}</p>}
      <Button type="submit" size="lg" block disabled={saving}>{saving ? "Сохраняем…" : submitLabel}<ArrowRight /></Button>
    </form>
  );
};
