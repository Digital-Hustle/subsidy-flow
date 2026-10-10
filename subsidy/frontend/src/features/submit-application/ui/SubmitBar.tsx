import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { applicantApi, applicantKeys } from "@/entities/application";
import { Button, toast } from "@/shared/ui";
import { ApiError, errorText } from "@/shared/api";

/** Отправить заявку специалисту. Полноту решает сервер: не хватает документов или полей анкеты — 422 со списком. */
export const SubmitButton = ({ applicationId, ready, titleOf }: { applicationId: string; ready: boolean; titleOf: (t: string) => string }) => {
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: () => applicantApi.submit(applicationId),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) });
      if (r?.missing?.length) toast.info(`Отправлено. Не хватает: ${r.missing.map(titleOf).join(", ")}`);
      else toast.success("Заявка отправлена специалисту");
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === "DOCUMENTS_MISSING") {
        const docs = (e.params.missing as string[] | undefined) ?? [];
        const fields = (e.params.missingFields as string[] | undefined) ?? [];
        toast.error([docs.length ? `Не хватает: ${docs.map(titleOf).join(", ")}` : "", fields.length ? `заполните в анкете: ${fields.length}` : ""].filter(Boolean).join("; ") || errorText(e));
      } else toast.error(errorText(e));
    },
  });
  return (
    <Button size="lg" block variant={ready ? "brand" : "primary"} disabled={m.isPending} onClick={() => m.mutate()}>
      <Send />{m.isPending ? "Отправляем…" : ready ? "Отправить заявку" : "Отправить заявку"}
    </Button>
  );
};
