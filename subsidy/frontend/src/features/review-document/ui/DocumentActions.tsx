import { useQuery } from "@tanstack/react-query";
import { RotateCcw } from "lucide-react";
import { reviewApi, reviewKeys, type DocumentCard } from "@/entities/application";
import { Button, Select } from "@/shared/ui";
import { useReprocessFile, useRetypeDocument } from "../model/mutations";

/** Сменить вид (переизвлечение по новой схеме) и разобрать файл заново (старший специалист). */
export const DocumentActions = ({ packageId, doc, canAct, canReprocess }: { packageId: string; doc: DocumentCard; canAct: boolean; canReprocess: boolean }) => {
  const types = useQuery({ queryKey: reviewKeys.types, queryFn: reviewApi.types, staleTime: Infinity, enabled: canAct && doc.real });
  const retype = useRetypeDocument(packageId);
  const reprocess = useReprocessFile(packageId);
  const options = (types.data ?? []).map((t) => ({ value: t.code, label: t.name ?? t.code }));
  if (!(canAct && doc.real && options.length) && !canReprocess) return null;
  return (
    <div className="flex flex-wrap items-center gap-2">
      {canAct && doc.real && options.length > 0 && (
        <Select size="sm" aria-label="Вид документа" className="w-64" value={doc.type ?? ""} options={options}
          onChange={(t) => t !== doc.type && retype.mutate({ docId: doc.id, type: t })} />
      )}
      {canReprocess && <Button size="sm" variant="secondary" disabled={reprocess.isPending} onClick={() => reprocess.mutate(doc.fileId)}><RotateCcw />Разобрать файл заново</Button>}
    </div>
  );
};
