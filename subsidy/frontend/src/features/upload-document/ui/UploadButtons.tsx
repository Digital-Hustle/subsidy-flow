import { useRef } from "react";
import { DOCUMENT_ACCEPT } from "@/entities/document";
import { Upload } from "lucide-react";
import { Button, type ButtonProps } from "@/shared/ui";

/** Выбор документа в одном из форматов исходного кейса. */
export const UploadButtons = ({ onFile, disabled, label = "Загрузить документ", size = "md", variant = "primary" }: {
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
      <input ref={pick} type="file" accept={DOCUMENT_ACCEPT} disabled={disabled} hidden onChange={take} />
      <Button size={size} variant={variant} disabled={disabled} onClick={() => pick.current?.click()}><Upload />{label}</Button>
    </div>
  );
};
