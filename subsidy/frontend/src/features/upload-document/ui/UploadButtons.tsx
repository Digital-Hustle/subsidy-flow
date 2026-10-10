import { useRef } from "react";
import { Upload } from "lucide-react";
import { Button, type ButtonProps } from "@/shared/ui";

/** Выбор PDF-файла: заявки на субсидию принимаются только в PDF (сканы и выгрузки с подписью). */
export const UploadButtons = ({ onFile, disabled, label = "Загрузить PDF", size = "md", variant = "primary" }: {
  onFile: (f: File) => void; disabled?: boolean; label?: string; size?: ButtonProps["size"]; variant?: ButtonProps["variant"];
}) => {
  const pick = useRef<HTMLInputElement>(null);
  const take = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = "";
    if (f) onFile(f);
  };
  return (
    <div className="flex flex-wrap gap-2">
      <input ref={pick} type="file" accept="application/pdf,.pdf" hidden onChange={take} />
      <Button size={size} variant={variant} disabled={disabled} onClick={() => pick.current?.click()}><Upload />{label}</Button>
    </div>
  );
};
