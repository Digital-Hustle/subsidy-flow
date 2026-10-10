import { useMutation, useQueryClient } from "@tanstack/react-query";
import { DatabaseZap } from "lucide-react";
import { packageKeys } from "@/entities/package";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, toast } from "@/shared/ui";

/** RUL-18 (SENIOR): сверить данные проверки с результатами разбора, если протокол отстал от документов. Перепроверка — на сервере. */
export const RebuildButton = ({ packageId }: { packageId: string }) => {
  const qc = useQueryClient();
  const rebuild = useMutation({
    mutationFn: () => protocolApi.rebuild(packageId),
    onSuccess: (r) => {
      toast.info(r.updated || r.removed ? `Данные сверены: обновлено ${r.updated}, убрано ${r.removed} — протокол пересчитывается` : "Данные совпадают с разбором — протокол пересчитывается");
      void qc.invalidateQueries({ queryKey: protocolKeys.latest(packageId) });
      void qc.invalidateQueries({ queryKey: packageKeys.card(packageId) });
    },
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <Button variant="secondary" disabled={rebuild.isPending} onClick={() => rebuild.mutate()} title="Сверить данные проверки с результатами разбора документов">
      <DatabaseZap />Сверить с разбором
    </Button>
  );
};
