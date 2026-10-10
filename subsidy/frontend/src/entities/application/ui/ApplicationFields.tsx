import { DatePicker, Field, Input, Select, Button } from "@/shared/ui";
import { APPLICATION_LABELS, needsProperty, type ApplicationDraft } from "../model/application-form";

type Key = keyof ApplicationDraft;
export const ApplicationFields = ({ value, onChange, programs, errors = {}, editing = false }: {
  value: ApplicationDraft; onChange: (value: ApplicationDraft) => void;
  programs: { value: string; label: string }[]; errors?: Partial<Record<Key, string>>; editing?: boolean;
}) => {
  const set = (key: Key, v: string) => onChange({ ...value, [key]: v });
  const field = (key: Key, mode?: "numeric" | "decimal") => (
    <Field key={key} label={APPLICATION_LABELS[key]} error={errors[key]}>
      <Input value={value[key]} inputMode={mode} onChange={(e) => set(key, mode === "numeric" ? e.target.value.replace(/\D/g, "") : e.target.value)} />
    </Field>
  );
  return <div className="flex flex-col gap-5">
    <p className="text-sm text-muted-foreground">Обязательны программа и ИНН. Остальные сведения можно заполнить позже. Проверки документов выполняются по извлечённым данным.</p>
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label={APPLICATION_LABELS.programCode} error={errors.programCode} hint={editing ? "Программа выбрана при создании пакета" : undefined}>
        {editing || !programs.length ? <Input readOnly value={editing ? programs.find((p) => p.value === value.programCode)?.label ?? value.programCode : "Программы недоступны"} /> : <Select value={value.programCode} onChange={(v) => set("programCode", v)} options={programs} />}
      </Field>
      {field("orgInn", "numeric")}{field("orgName")}{field("ogrn", "numeric")}{field("address")}
      <Field label={APPLICATION_LABELS.addressPurpose} error={errors.addressPurpose}><Select value={value.addressPurpose} onChange={(v) => set("addressPurpose", v)} options={[{ value: "LEGAL", label: "Юридический" }, { value: "ACTUAL", label: "Фактический" }, { value: "PROJECT", label: "Адрес проекта" }]} /></Field>
    </div>
    <section className="flex flex-col gap-3" aria-label="Проект и заявка">
      <h3 className="font-semibold">Проект и заявка</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        {field("project")}{field("requestedAmount", "decimal")}{field("totalCost", "decimal")}{field("ownFunds", "decimal")}
        <div className="flex flex-col gap-1.5">
          <Field label={APPLICATION_LABELS.applicationDate} error={errors.applicationDate}><DatePicker value={value.applicationDate} onChange={(v) => set("applicationDate", v)} /></Field>
          {value.applicationDate && <Button size="sm" variant="ghost" onClick={() => set("applicationDate", "")}>Очистить дату</Button>}
        </div>
        {field("signerName")}{field("signerPosition")}{field("signerAuthority")}
      </div>
    </section>
    {needsProperty(value.programCode) && <section className="flex flex-col gap-3" aria-label="Сведения об участке">
      <h3 className="font-semibold">Сведения об участке</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        {field("cadastralNumber")}{field("area", "decimal")}{field("purpose")}{field("propertyAddress")}{field("rightHolder")}
        <Field label={APPLICATION_LABELS.rightType} error={errors.rightType}><Select value={value.rightType} onChange={(v) => set("rightType", v)} options={[{ value: "", label: "Не указан" }, { value: "OWNERSHIP", label: "Собственность" }, { value: "LEASE", label: "Аренда" }, { value: "USE", label: "Пользование" }]} /></Field>
      </div>
    </section>}
  </div>;
};
