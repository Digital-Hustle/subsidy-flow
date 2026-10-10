import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilLine, Plus } from "lucide-react";
import { ApplicationFields, applicationDraft, applicationErrors, applicationPayload, canSubmitApplication, reviewApi, reviewKeys, type Card } from "@/entities/application";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { Button, Dialog, Status, useDialogState } from "@/shared/ui";
import { routes } from "@/shared/const/router";

const ApplicationDialog = ({ card, onClosed }: { card?: Card; onClosed: () => void }) => {
  const { open, close } = useDialogState();
  const [draft, setDraft] = useState(() => applicationDraft(card?.program, card?.application, card?.org.inn));
  const [key, setKey] = useState(() => crypto.randomUUID());
  const [revision] = useState(card?.applicationRevision);
  const qc = useQueryClient();
  const nav = useNavigate();
  const programs = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs });
  const options = (programs.data ?? []).map((p) => ({ value: p.code, label: p.name ?? p.code }));
  const value = { ...draft, programCode: draft.programCode || options[0]?.value || "" };
  const supported = !card || typeof revision === "number";
  const save = useMutation({
    mutationFn: async () => card
      ? (await reviewApi.updateApplication(card.id, revision!, applicationPayload(value)), card.id)
      : (await reviewApi.create(value.orgInn, value.programCode, applicationPayload(value), key)).packageId,
    onSuccess: (id) => {
      void qc.invalidateQueries({ queryKey: reviewKeys.card(id) });
      void qc.invalidateQueries({ queryKey: reviewKeys.queueAll });
      void qc.invalidateQueries({ queryKey: reviewKeys.history(id) });
      void qc.invalidateQueries({ queryKey: protocolKeys.latest(id) });
      void qc.invalidateQueries({ queryKey: protocolKeys.versions(id) });
      close(); if (!card) nav(routes.operatorApplication(id));
    },
  });
  const errors = save.error instanceof ApiError ? applicationErrors(save.error.params, save.error.message) : {};
  if (save.error instanceof ApiError && save.error.code === "INN_INVALID") errors.orgInn = save.error.message;
  const valid = supported && canSubmitApplication(value) && !save.isPending && !programs.isError && card?.status !== "DECIDED";
  return <Dialog open={open} onClose={() => { if (!save.isPending) close(); }} onClosed={onClosed} title={card ? "Редактирование заявки" : "Новая заявка"} description="Введённые сведения хранятся отдельно от разбора документов" className="sm:max-w-3xl"
    footer={<Button form="application-details" type="submit" disabled={!valid}>{save.isPending ? "Сохраняем…" : card ? "Сохранить" : "Создать"}</Button>}>
    {!supported && <Status tone="warning" className="mb-4">Сервер пока не передаёт ревизию заявки. Сохранение станет доступно после обновления API.</Status>}
    <form id="application-details" onSubmit={(e) => { e.preventDefault(); if (valid) save.mutate(); }}>
      <fieldset disabled={save.isPending || !supported} className="min-w-0"><ApplicationFields editing={!!card} value={value} programs={options} errors={errors} onChange={(d) => { setDraft(d); setKey(crypto.randomUUID()); save.reset(); }} /></fieldset>
      {programs.isError && <Status tone="danger" className="mt-4">{errorText(programs.error)} <Button size="sm" variant="ghost" onClick={() => programs.refetch()}>Повторить</Button></Status>}
      {save.isError && <p role="alert" className="mt-4 text-sm text-danger">{errorText(save.error)}{save.error instanceof ApiError && save.error.hint ? ` — ${save.error.hint}` : ""}</p>}
      {save.error instanceof ApiError && typeof save.error.params.packageId === "string" && <Link className="mt-4 block text-sm text-brand underline" to={routes.operatorApplication(save.error.params.packageId)}>Открыть созданную заявку</Link>}
    </form>
  </Dialog>;
};
export const CreateApplicationButton = () => {
  const [editing, setEditing] = useState(false);
  return <><Button onClick={() => setEditing(true)}><Plus />Новая заявка</Button>{editing && <ApplicationDialog onClosed={() => setEditing(false)} />}</>;
};
export const EditApplicationButton = ({ card }: { card: Card }) => {
  const [editing, setEditing] = useState(false);
  return <><Button variant="secondary" onClick={() => setEditing(true)}><PencilLine />Редактировать</Button>{editing && <ApplicationDialog card={card} onClosed={() => setEditing(false)} />}</>;
};
