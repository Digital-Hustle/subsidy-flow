import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Check, ChevronDown } from "lucide-react";
import { cn } from "@/shared/lib";
import { spring, tween } from "@/shared/config/motion";
import { inputClass } from "./Input";

export type SelectOption<T extends string> = { value: T; label: string; hint?: string };

/** Кастомный селект: поповер в портале (не обрезается шторкой), клавиатура, переворот вверх у края экрана. */
export function Select<T extends string>({ value, onChange, options, className, placeholder = "Выберите", id, size = "md", "aria-label": ariaLabel, ...rest }: {
  value: T; onChange: (v: T) => void; options: SelectOption<T>[]; className?: string; placeholder?: string; id?: string; size?: "sm" | "md"; "aria-label"?: string; "aria-describedby"?: string; "aria-invalid"?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [pos, setPos] = useState<{ left: number; top?: number; bottom?: number; width: number; maxH: number } | null>(null);
  const btn = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const lid = useId();
  const cur = options.find((o) => o.value === value);

  const place = useCallback(() => {
    const r = btn.current?.getBoundingClientRect();
    if (!r) return;
    const below = innerHeight - r.bottom - 12, above = r.top - 12;
    const width = Math.max(r.width, 200);
    const left = Math.max(8, Math.min(r.left, innerWidth - width - 8));
    const up = below < 220 && above > below;
    setPos(up ? { left, bottom: innerHeight - r.top + 6, width, maxH: Math.min(320, above) } : { left, top: r.bottom + 6, width, maxH: Math.min(320, below) });
  }, []);

  useLayoutEffect(() => { if (open) place(); }, [open, place]);
  useEffect(() => {
    if (!open) return;
    setActive(Math.max(0, options.findIndex((o) => o.value === value)));
    const close = (e: Event) => { if (!list.current?.contains(e.target as Node) && !btn.current?.contains(e.target as Node)) setOpen(false); };
    const reflow = () => place();
    addEventListener("pointerdown", close, true);
    addEventListener("resize", reflow);
    addEventListener("scroll", reflow, true);
    return () => { removeEventListener("pointerdown", close, true); removeEventListener("resize", reflow); removeEventListener("scroll", reflow, true); };
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (open) list.current?.querySelector<HTMLElement>(`[data-i="${active}"]`)?.scrollIntoView({ block: "nearest" }); }, [active, open]);

  const pick = (i: number) => { const o = options[i]; if (o) onChange(o.value); setOpen(false); btn.current?.focus(); };
  const onKey = (e: React.KeyboardEvent) => {
    if (!open && ["ArrowDown", "ArrowUp", "Enter", " "].includes(e.key)) { e.preventDefault(); setOpen(true); return; }
    if (!open) return;
    if (e.key === "Escape") { e.preventDefault(); setOpen(false); }
    else if (e.key === "ArrowDown") { e.preventDefault(); setActive((a) => Math.min(options.length - 1, a + 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActive((a) => Math.max(0, a - 1)); }
    else if (e.key === "Home") { e.preventDefault(); setActive(0); }
    else if (e.key === "End") { e.preventDefault(); setActive(options.length - 1); }
    else if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(active); }
    else if (e.key === "Tab") setOpen(false);
    else if (e.key.length === 1) { const i = options.findIndex((o) => o.label.toLowerCase().startsWith(e.key.toLowerCase())); if (i >= 0) setActive(i); }
  };

  return (
    <>
      <button ref={btn} id={id} type="button" role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={lid} aria-label={ariaLabel} {...rest}
        onClick={() => setOpen((o) => !o)} onKeyDown={onKey}
        className={cn(inputClass, "flex items-center justify-between gap-2 text-left", size === "sm" && "h-control-sm rounded-sm text-sm", className)}>
        <span className={cn("min-w-0 truncate", !cur && "text-subtle-foreground")}>{cur?.label ?? placeholder}</span>
        <motion.span animate={{ rotate: open ? 180 : 0 }} transition={spring.snappy} className="flex shrink-0 text-muted-foreground"><ChevronDown className="size-4" /></motion.span>
      </button>
      {createPortal(
        <AnimatePresence>
          {open && pos && (
            <motion.div ref={list} id={lid} role="listbox" tabIndex={-1} aria-activedescendant={`${lid}-${active}`}
              initial={{ opacity: 0, y: pos.top !== undefined ? -6 : 6, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, scale: 0.98, transition: tween.exit }}
              transition={{ ...spring.snappy, opacity: tween.fast }}
              className={cn("fixed z-dropdown overflow-y-auto overscroll-contain rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-pop", pos.top !== undefined ? "origin-top" : "origin-bottom")}
              style={{ left: pos.left, top: pos.top, bottom: pos.bottom, width: pos.width, maxHeight: pos.maxH }}>
              {options.map((o, i) => {
                const sel = o.value === value;
                return (
                  <div key={o.value} id={`${lid}-${i}`} data-i={i} role="option" aria-selected={sel}
                    onPointerEnter={() => setActive(i)} onClick={() => pick(i)}
                    className={cn("flex min-h-control-sm cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm", i === active && "bg-surface")}>
                    <span className="min-w-0 flex-1"><span className="block truncate">{o.label}</span>{o.hint && <span className="block truncate text-xs text-muted-foreground">{o.hint}</span>}</span>
                    {sel && <Check className="size-4 shrink-0 text-brand" />}
                  </div>
                );
              })}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </>
  );
}
