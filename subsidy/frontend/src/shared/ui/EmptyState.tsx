import { cn } from "@/shared/lib";

export const EmptyState = ({ icon, title, text, action, className }: { icon?: React.ReactNode; title: string; text?: string; action?: React.ReactNode; className?: string }) => (
  <div className={cn("flex flex-col items-center justify-center gap-3 px-6 py-12 text-center", className)}>
    {icon && <div className="flex size-12 items-center justify-center rounded-md bg-surface text-muted-foreground [&_svg]:size-6">{icon}</div>}
    <div className="font-display text-lg font-semibold tracking-display">{title}</div>
    {text && <p className="max-w-sm text-balance text-sm text-muted-foreground">{text}</p>}
    {action}
  </div>
);
