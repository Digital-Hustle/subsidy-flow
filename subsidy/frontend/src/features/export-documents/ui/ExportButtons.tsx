import { useState } from "react";
import { FileDown } from "lucide-react";
import { reviewApi } from "@/entities/application";
import { Button, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

const run = async (f: () => Promise<void>, set: (b: boolean) => void) => {
  set(true);
  try { await f(); } catch (e) { toast.error(errorText(e)); } finally { set(false); }
};

/** Протокол проверки (DOCX/XLSX/PDF) — формирует rules, выгрузка пишется в историю. */
export const ExportProtocolButtons = ({ packageId }: { packageId: string }) => {
  const [busy, setBusy] = useState(false);
  return (
    <div className="flex gap-1.5">
      {(["docx", "xlsx", "pdf"] as const).map((f) => (
        <Button key={f} size="sm" variant="secondary" disabled={busy} onClick={() => run(() => reviewApi.exportProtocol(packageId, f), setBusy)}><FileDown />{f.toUpperCase()}</Button>
      ))}
    </div>
  );
};
