import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { packageApi, packageKeys } from "@/entities/package";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { Button, Dialog, Field, Input, Select } from "@/shared/ui";
import { routes } from "@/shared/const/router";

const INN_RE = /^\d{10}$|^\d{12}$/;

/** FE-PK-01: ИНН (контрольную сумму проверяет сервер) + программа → пакет, Idempotency-Key от двойного клика. */
export const CreatePackageDialog = () => {
  const [sp, setSp] = useSearchParams();
  // Кнопка «Новый пакет» одна — в шапке; она ведёт сюда с ?new=1, диалог открывается сразу
  const open = sp.has("new");
  const setOpen = (v: boolean) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set("new", "1"); else n.delete("new"); return n; }, { replace: true });
  const [inn, setInn] = useState("");
  const [program, setProgram] = useState("");
  const nav = useNavigate();
  const qc = useQueryClient();
  const programs = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs, enabled: open });
  const options = (programs.data?.items ?? []).map((p) => ({ value: p.code, label: `${p.name} (${p.code})` }));
  const chosen = program || options[0]?.value || "";
  const create = useMutation({
    mutationFn: () => packageApi.create(inn, chosen),
    onSuccess: (p) => { void qc.invalidateQueries({ queryKey: packageKeys.queueAll }); setOpen(false); nav(routes.pkg(p.packageId)); },
  });
  const innError = create.error instanceof ApiError && create.error.code === "INN_INVALID" ? create.error.message : null;
  const valid = INN_RE.test(inn) && !!chosen;
  return (
    <Dialog open={open} onClose={() => setOpen(false)} title="Новый пакет" description="Пакет документов организации на субсидию"
      footer={<Button disabled={!valid || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Создаём…" : "Создать"}</Button>}>
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (valid) create.mutate(); }}>
        <Field label="ИНН организации" hint="10 цифр для юрлица, 12 — для ИП" error={innError}>
          <Input inputMode="numeric" autoFocus value={inn} maxLength={12} onChange={(e) => setInn(e.target.value.replace(/\D/g, ""))} placeholder="7707083893" />
        </Field>
        <Field label="Программа субсидии">
          <Select aria-label="Программа" value={chosen} onChange={setProgram} options={options} placeholder={programs.isPending ? "Загружаем…" : "Выберите"} />
        </Field>
        {create.isError && !innError && <p className="text-sm text-danger">{errorText(create.error)}</p>}
      </form>
    </Dialog>
  );
};
