import { useState } from "react";
import { Download } from "lucide-react";
import { protocolApi } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, toast } from "@/shared/ui";

const FORMATS = ["docx", "xlsx", "pdf"] as const;

/** FE-SP-05: выгрузка протокола в DOCX/XLSX/PDF — файл формирует rules. */
export const ExportButtons = ({ packageId }: { packageId: string }) => {
  const [busy, setBusy] = useState<string | null>(null);
  const run = (f: (typeof FORMATS)[number]) => {
    setBusy(f);
    protocolApi.exportFile(packageId, f).catch((e: unknown) => toast.error(errorText(e))).finally(() => setBusy(null));
  };
  return (
    <div className="flex gap-1">
      {FORMATS.map((f) => (
        <Button key={f} size="sm" variant="secondary" disabled={!!busy} onClick={() => run(f)}><Download />{busy === f ? "…" : f.toUpperCase()}</Button>
      ))}
    </div>
  );
};
