import { cloneElement, forwardRef, isValidElement, useId } from "react";
import { cn } from "@/shared/lib";

export const inputClass = "h-control-md w-full min-w-0 text-ellipsis rounded-md border border-input bg-card px-3.5 text-base text-foreground outline-none transition-[border-color,box-shadow] duration-fast placeholder:text-subtle-foreground hover:border-border-strong focus:border-ring focus:ring-4 focus:ring-ring/15 disabled:opacity-50 aria-invalid:border-danger aria-invalid:ring-danger/15 sm:text-sm";

export const Input = forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement> & { icon?: React.ReactNode }>(({ className, icon, ...p }, ref) =>
  icon ? (
    <span className="relative flex min-w-0 items-center">
      <span className="pointer-events-none absolute left-3.5 flex text-subtle-foreground [&_svg]:size-4.5">{icon}</span>
      <input ref={ref} className={cn(inputClass, "pl-10", className)} {...p} />
    </span>
  ) : <input ref={ref} className={cn(inputClass, className)} {...p} />,
);
Input.displayName = "Input";

/** Поле формы: подпись сверху, подсказка или ошибка под полем. Привязывает id/aria к ребёнку. */
export const Field = ({ label, hint, error, children, className }: { label: string; hint?: React.ReactNode; error?: string | null; children: React.ReactElement<Record<string, unknown>>; className?: string }) => {
  const id = useId();
  const describedBy = error || hint ? `${id}-d` : undefined;
  const child = isValidElement(children) ? cloneElement(children, { id, "aria-describedby": describedBy, "aria-invalid": error ? true : undefined }) : children;
  return (
    <div className={cn("flex min-w-0 flex-col gap-1.5", className)}>
      <label htmlFor={id} className="text-sm font-medium text-foreground">{label}</label>
      {child}
      {(error || hint) && <p id={describedBy} className={cn("text-xs", error ? "text-danger" : "text-muted-foreground")}>{error || hint}</p>}
    </div>
  );
};
