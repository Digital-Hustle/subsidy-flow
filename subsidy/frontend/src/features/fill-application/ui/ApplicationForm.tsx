import { useState } from "react";
import { ApplicationFields, applicationDraft, applicationErrors, applicationPayload, type ApplicationData } from "@/entities/application";
import { Button } from "@/shared/ui";

export const ApplicationForm = ({ initial, programCode = "", onSave, saving, submitLabel = "Сохранить и перейти к документам", serverError }: {
  initial?: ApplicationData | null; programCode?: string; onSave: (a: ApplicationData) => void; saving?: boolean; submitLabel?: string; serverError?: { field?: string; text: string } | null;
}) => {
  const [draft, setDraft] = useState(() => applicationDraft(programCode, initial));
  const errors = serverError ? applicationErrors({ field: serverError.field }, serverError.text) : {};
  return <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); if (!saving) onSave(applicationPayload(draft)); }}>
    <fieldset disabled={saving} className="min-w-0"><ApplicationFields editing value={draft} onChange={setDraft} errors={errors} programs={[]} /></fieldset>
    {serverError && <p className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger-soft-foreground" role="alert">{serverError.text}</p>}
    <Button type="submit" size="lg" block disabled={saving}>{saving ? "Сохраняем…" : submitLabel}</Button>
  </form>;
};
