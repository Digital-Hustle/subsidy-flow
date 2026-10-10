import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FilePlus2 } from "lucide-react";
import { reviewApi, reviewKeys } from "@/entities/application";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { Button, Dialog, Field, Input, Select } from "@/shared/ui";
import { routes } from "@/shared/const/router";

const INN_RE = /^\d{10}$|^\d{12}$/;

/** Заявка от специалиста: документы принесли не через кабинет. ИНН (контрольную сумму проверяет сервер) + программа → пакет. */
export const CreatePackageButton = () => {
  const [open, setOpen] = useState(false);
  const [inn, setInn] = useState("");
  const [program, setProgram] = useState("");
  const nav = useNavigate();
  const qc = useQueryClient();
  const programs = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs, enabled: open });
  const options = (programs.data ?? []).filter((p) => p.accepting !== false).map((p) => ({ value: p.code, label: p.name ?? p.code }));
  const chosen = program || options[0]?.value || "";
  const create = useMutation({
    mutationFn: () => reviewApi.create(inn, chosen),
    onSuccess: (p) => { void qc.invalidateQueries({ queryKey: reviewKeys.queueAll }); setOpen(false); nav(routes.operatorApplication(p.packageId)); },
  });
  const innError = create.error instanceof ApiError && create.error.code === "INN_INVALID" ? errorText(create.error) : null;
  const valid = INN_RE.test(inn) && !!chosen;
  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}><FilePlus2 />Новая заявка</Button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Новая заявка" description="Документы организации принесли не через кабинет заявителя — загрузите их сами"
        footer={<Button disabled={!valid || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Создаём…" : "Создать"}</Button>}>
        <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (valid) create.mutate(); }}>
          <Field label="ИНН организации" hint="10 цифр для юрлица, 12 — для ИП" error={innError}>
            <Input inputMode="numeric" autoFocus value={inn} maxLength={12} onChange={(e) => setInn(e.target.value.replace(/\D/g, ""))} placeholder="7707083893" />
          </Field>
          <Field label="Программа поддержки">
            <Select aria-label="Программа" value={chosen} onChange={setProgram} options={options} placeholder={programs.isPending ? "Загружаем…" : "Выберите"} />
          </Field>
          {create.isError && !innError && <p className="text-sm text-danger">{errorText(create.error)}</p>}
        </form>
      </Dialog>
    </>
  );
};
