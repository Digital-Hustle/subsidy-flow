import { cn } from "@/shared/lib";

/**
 * Знак «Субсидий»: суперэллипс с конусом брендбука Сбера и галочкой-проходом.
 * Мягкость — конус размыт внутри маски, сверху блик (bg-sheen) и тонкая внутренняя подсветка.
 */
/**
 * Эталонная заливка конусом Сбера, как в знаке: размытый конус + блик. Родитель — relative overflow-hidden.
 * Размытый слой в Safari вылезает за скругление родителя — режем clip-path по радиусу родителя (radius, CSS-длина).
 */
export const BrandConicFill = ({ radius = "var(--radius-sm)" }: { radius?: string }) => (
  <span aria-hidden className="clip-round absolute inset-0 overflow-hidden rounded-[inherit]" style={{ "--clip-r": radius } as React.CSSProperties}>
    <span className="absolute -inset-1/2 bg-brand-conic blur-sm" />
    <span className="absolute inset-0 bg-sheen opacity-70" />
  </span>
);

export const LogoMark = ({ className }: { className?: string }) => (
  <span className={cn("relative isolate flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-sm shadow-glow", className)}>
    <BrandConicFill />
    <span aria-hidden className="absolute inset-0 rounded-sm ring-1 ring-inset ring-white/25" />
    <svg viewBox="0 0 24 24" className="relative size-3/5 text-white drop-shadow-sm" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  </span>
);

export const Logo = ({ className, compact, sub }: { className?: string; compact?: boolean; sub?: string }) => (
  <span className={cn("inline-flex min-w-0 items-center gap-2.5", className)}>
    <LogoMark />
    {!compact && (
      <span className="flex min-w-0 flex-col leading-none">
        <span className="truncate font-display text-lg font-semibold tracking-display">Субсидии</span>
        {sub && <span className="mt-0.5 truncate text-xs text-muted-foreground">{sub}</span>}
      </span>
    )}
  </span>
);
