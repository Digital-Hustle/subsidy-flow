import { forwardRef } from "react";
import { cn } from "@/shared/lib";
import { inputClass } from "./Input";

/** Многострочное поле в стиле Input: высота по содержимому задаётся rows. */
export const Textarea = forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(({ className, rows = 3, ...p }, ref) => (
  <textarea ref={ref} rows={rows} className={cn(inputClass, "h-auto resize-y py-2.5 leading-normal", className)} {...p} />
));
Textarea.displayName = "Textarea";
