import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/shared/lib";

/**
 * Статус — плоская метка (ADR-048): тинт фона и цвет текста, без рамки, без точки, почти прямые углы.
 * Не «пилюля в рамке»: статус читается цветом и словом, а не обвязкой.
 */
const status = cva("inline-flex h-6 max-w-full shrink-0 items-center gap-1 whitespace-nowrap rounded-2xs px-2 text-xs font-medium leading-none [&_svg]:size-3.5 [&_svg]:shrink-0", {
  variants: {
    tone: {
      neutral: "bg-surface text-muted-foreground",
      success: "bg-success-soft text-success-soft-foreground",
      warning: "bg-warning-soft text-warning-soft-foreground",
      danger: "bg-danger-soft text-danger-soft-foreground",
      info: "bg-info-soft text-info-soft-foreground",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export type Tone = NonNullable<VariantProps<typeof status>["tone"]>;

export const Status = ({ tone = "neutral", className, children, ...p }: React.HTMLAttributes<HTMLSpanElement> & VariantProps<typeof status>) => (
  <span className={cn(status({ tone }), className)} {...p}>
    <span className="flex min-w-0 items-center gap-1 truncate">{children}</span>
  </span>
);
/** Совместимость: старое имя */
export const Badge = Status;
