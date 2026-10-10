import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { applicantApi, applicantKeys } from "@/entities/application";
import { Button, Dialog, toast } from "@/shared/ui";
import { errorText, setAuth } from "@/shared/api";
import { applicationKey } from "@/shared/lib";
import { routes } from "@/shared/const/router";

/** NFR-10: удалить заявку или отозвать согласие — файлы, анкета и результаты стираются, ссылка перестаёт работать. */
export const DeleteApplication = ({ applicationId }: { applicationId: string }) => {
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const nav = useNavigate();
  const m = useMutation({
    mutationFn: () => applicantApi.deleteApplication(applicationId),
    onSuccess: () => {
      applicationKey.clear(applicationId);
      setAuth(() => null);
      qc.removeQueries({ queryKey: applicantKeys.application(applicationId) });
      toast.success("Заявка и ваши данные удалены");
      void nav(routes.apply, { replace: true });
    },
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <>
      <Button variant="quiet" size="sm" onClick={() => setOpen(true)}><Trash2 />Удалить заявку и мои данные</Button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Удалить заявку?"
        description="Это отзыв согласия на обработку данных. Файлы, ответы анкеты и распознанные данные удалятся без возможности восстановления, ссылка и код перестанут работать."
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)}>Оставить</Button>
          <Button variant="danger" disabled={m.isPending} onClick={() => m.mutate()}><Trash2 />{m.isPending ? "Удаляем…" : "Удалить"}</Button>
        </>}>
        <p className="text-sm text-muted-foreground">Если заявка уже у специалиста, он увидит, что заявка удалена, но не её содержимое.</p>
      </Dialog>
    </>
  );
};
