import { cn } from "@/shared/lib";

// Спокойные тинты из статусной палитры — никаких кислотных заливок
const TONES = ["bg-success-soft text-success-soft-foreground", "bg-info-soft text-info-soft-foreground", "bg-warning-soft text-warning-soft-foreground", "bg-surface text-foreground", "bg-accent text-accent-foreground"];

export const Avatar = ({ name, photo, className }: { name: string; photo?: string; className?: string }) => {
  const initials = name.split(" ").slice(0, 2).map((p) => p[0]).join("");
  const tone = TONES[[...name].reduce((s, c) => s + c.charCodeAt(0), 0) % TONES.length];
  return photo ? (
    <img src={photo} alt={name} className={cn("size-10 shrink-0 rounded-full object-cover", className)} />
  ) : (
    <span className={cn("flex size-10 shrink-0 select-none items-center justify-center rounded-full text-sm font-medium", tone, className)} aria-label={name}>{initials}</span>
  );
};
