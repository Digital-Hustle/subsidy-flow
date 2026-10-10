import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reviewApi, reviewKeys } from "@/entities/application";
import { protocolKeys } from "@/entities/protocol";
import { toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

/** Все действия специалиста над документом: после успеха — свежая карточка, протокол и очередь. */
const useAct = <V,>(packageId: string, fn: (v: V) => Promise<unknown>, ok: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      toast.success(ok);
      void qc.invalidateQueries({ queryKey: reviewKeys.card(packageId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.history(packageId) });
      void qc.invalidateQueries({ queryKey: protocolKeys.latest(packageId) });
      void qc.invalidateQueries({ queryKey: protocolKeys.versions(packageId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.queueAll });
    },
    onError: (e) => toast.error(errorText(e)),
  });
};

/** Правка поля — данные, не вердикт: rules перепроверит пакет и выпустит новую версию протокола. */
export const useCorrectField = (packageId: string) =>
  useAct(packageId, (v: { docId: string; name: string; value: string; reason?: string }) => reviewApi.correct(packageId, v.docId, v.name, v.value, v.reason), "Поле исправлено — протокол пересчитывается");
export const useRetypeDocument = (packageId: string) =>
  useAct(packageId, (v: { docId: string; type: string }) => reviewApi.retype(v.docId, v.type), "Вид изменён — поля извлекаются заново");
export const useReprocessFile = (packageId: string) => useAct(packageId, (fileId: string) => reviewApi.reprocess(fileId), "Файл отправлен на повторный разбор");
export const useRebuild = (packageId: string) => useAct(packageId, () => reviewApi.rebuild(packageId), "Документы пересобраны — протокол пересчитывается");
/** Взять заявку в работу (force — забрать у коллеги, только старший). */
export const useAssign = (packageId: string) => useAct(packageId, (force: boolean) => reviewApi.assign(packageId, force), "Заявка назначена вам");
