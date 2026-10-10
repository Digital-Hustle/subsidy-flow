import { cn } from "@/shared/lib";

/** Панель как у Сбера: белая на мятно-сером фоне, радиус 24 на десктопе, без рамки в светлой теме. */
export const Card = ({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("min-w-0 rounded-lg bg-card text-card-foreground shadow-card sm:rounded-xl", className)} {...p} />
);
export const CardHeader = ({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("flex min-w-0 items-center justify-between gap-3 px-4 pt-4 sm:px-6 sm:pt-5", className)} {...p} />
);
export const CardTitle = ({ className, ...p }: React.HTMLAttributes<HTMLHeadingElement>) => (
  <h2 className={cn("min-w-0 truncate font-display text-lg font-semibold tracking-display", className)} {...p} />
);
export const CardContent = ({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) => <div className={cn("p-4 sm:p-6", className)} {...p} />;
/** Утопленный блок внутри панели: радиус меньше на величину отступа */
export const Sunken = ({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("min-w-0 rounded-md bg-muted", className)} {...p} />
);
