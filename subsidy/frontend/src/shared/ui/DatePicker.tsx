import { AnimatePresence, motion } from "motion/react";
import { useLayoutEffect, useRef, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, Clock } from "lucide-react";
import { cn, todayKey } from "@/shared/lib";
import { popIn, spring, tween } from "@/shared/config/motion";

const Dot = ({ className }: { className?: string }) => <motion.span aria-hidden variants={popIn} initial="hidden" animate="show" className={cn("absolute inset-0 bg-primary", className)} />;
import { inputClass } from "./Input";
import { usePopover, PopoverPanel } from "./Popover";

const MONTHS = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"];
const WD = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
const key = (y: number, m: number, d: number) => todayKey(new Date(y, m, d));
export const formatDayRu = (k: string) => { const [y, m, d] = k.split("-").map(Number); return new Date(y, m - 1, d).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" }); };
const shortRu = (k: string) => { const [y, m, d] = k.split("-").map(Number); return new Date(y, m - 1, d).toLocaleDateString("ru-RU", { day: "numeric", month: "short" }); };

export type DateRange = { from: string; to: string };

/** Календарь на месяц: сдвиг по направлению при смене месяца, подсветка диапазона. */
export const Calendar = ({ value, range, onPick, min, max }: { value?: string; range?: Partial<DateRange>; onPick: (k: string) => void; min?: string; max?: string }) => {
  const start = value ?? range?.from ?? todayKey();
  const [ym, setYm] = useState(() => { const [y, m] = start.split("-").map(Number); return { y, m: m - 1 }; });
  const [dir, setDir] = useState(1);
  const [hover, setHover] = useState<string | null>(null);
  const shift = (d: number) => { setDir(d); setYm(({ y, m }) => { const n = new Date(y, m + d, 1); return { y: n.getFullYear(), m: n.getMonth() }; }); };
  const first = (new Date(ym.y, ym.m, 1).getDay() + 6) % 7;
  const days = new Date(ym.y, ym.m + 1, 0).getDate();
  const cells = [...Array(first).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)];
  const today = todayKey();
  const rFrom = range?.from, rTo = range?.to ?? (rFrom && hover && hover > rFrom ? hover : undefined);
  return (
    <div className="w-64 select-none">
      <div className="mb-2 flex items-center justify-between gap-2">
        <button type="button" onClick={() => shift(-1)} aria-label="Предыдущий месяц" className="flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors duration-fast hover:bg-surface hover:text-foreground"><ChevronLeft className="size-4" /></button>
        <div className="relative h-6 flex-1 overflow-hidden text-center text-sm font-semibold">
          <AnimatePresence initial={false} custom={dir} mode="popLayout">
            <motion.span key={`${ym.y}-${ym.m}`} custom={dir} className="absolute inset-0 leading-6"
              initial={{ opacity: 0, y: dir * 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -dir * 12, transition: tween.exit }} transition={{ ...spring.snappy, opacity: tween.fast }}>
              {MONTHS[ym.m]} {ym.y}
            </motion.span>
          </AnimatePresence>
        </div>
        <button type="button" onClick={() => shift(1)} aria-label="Следующий месяц" className="flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors duration-fast hover:bg-surface hover:text-foreground"><ChevronRight className="size-4" /></button>
      </div>
      <div className="grid grid-cols-7 text-center text-xs text-subtle-foreground">{WD.map((w) => <span key={w} className="py-1">{w}</span>)}</div>
      <div className="relative overflow-hidden">
        <AnimatePresence initial={false} custom={dir} mode="popLayout">
          <motion.div key={`${ym.y}-${ym.m}`} className="grid grid-cols-7 gap-y-0.5"
            initial={{ opacity: 0, x: dir * 40 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -dir * 40, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}>
            {cells.map((d, i) => {
              if (!d) return <span key={`e${i}`} />;
              const k = key(ym.y, ym.m, d);
              const off = (min && k < min) || (max && k > max);
              const sel = k === value || k === rFrom || k === rTo;
              const inR = rFrom && rTo && k > rFrom && k < rTo;
              return (
                <button key={k} type="button" disabled={!!off} onClick={() => onPick(k)} onPointerEnter={() => setHover(k)} aria-pressed={sel} aria-label={formatDayRu(k)}
                  className={cn("relative flex h-9 items-center justify-center text-sm tabular-nums transition-colors duration-fast disabled:opacity-30",
                    inR && "bg-accent text-accent-foreground", k === rFrom && rTo && "rounded-l-md bg-accent", k === rTo && rFrom && "rounded-r-md bg-accent",
                    !sel && !inR && "rounded-md hover:bg-surface")}>
                  {sel && <Dot className="rounded-md" />}
                  <span className={cn("relative", sel && "font-semibold text-primary-foreground", !sel && k === today && "font-semibold text-brand")}>{d}</span>
                </button>
              );
            })}
          </motion.div>
        </AnimatePresence>
      </div>
      <div className="mt-2 flex justify-between border-t border-border pt-2">
        <button type="button" onClick={() => { const [y, m] = today.split("-").map(Number); setDir(1); setYm({ y, m: m - 1 }); onPick(today); }} className="rounded-sm px-2 py-1 text-sm font-medium text-accent-foreground hover:bg-surface">Сегодня</button>
      </div>
    </div>
  );
};

const Trigger = ({ state, text, placeholder, icon, className, ...rest }: { state: ReturnType<typeof usePopover>; text?: string; placeholder: string; icon: React.ReactNode; className?: string; id?: string; "aria-describedby"?: string; "aria-invalid"?: boolean; "aria-label"?: string }) => (
  <button ref={state.anchor} type="button" aria-haspopup="dialog" aria-expanded={state.open} onClick={() => state.setOpen((o) => !o)} {...rest}
    className={cn(inputClass, "flex items-center gap-2 text-left", state.open && "border-ring ring-4 ring-ring/15", className)}>
    <span className="flex shrink-0 text-subtle-foreground [&_svg]:size-4">{icon}</span>
    <span className={cn("min-w-0 flex-1 truncate tabular-nums", !text && "text-subtle-foreground")}>{text || placeholder}</span>
  </button>
);

type Aria = { id?: string; "aria-describedby"?: string; "aria-invalid"?: boolean; "aria-label"?: string; className?: string };

export const DatePicker = ({ value, onChange, min, max, placeholder = "Выберите дату", ...rest }: Aria & { value: string; onChange: (v: string) => void; min?: string; max?: string; placeholder?: string }) => {
  const p = usePopover(290, 380);
  return (
    <>
      <Trigger state={p} text={value ? formatDayRu(value) : ""} placeholder={placeholder} icon={<CalendarDays />} {...rest} />
      <PopoverPanel state={p} label="Календарь"><Calendar value={value} min={min} max={max} onPick={(k) => { onChange(k); p.setOpen(false); }} /></PopoverPanel>
    </>
  );
};

/** Диапазон дат: первый клик — начало, второй — конец; быстрые пресеты слева. */
export const DateRangePicker = ({ value, onChange, max, presets = [], ...rest }: Aria & { value: DateRange; onChange: (v: DateRange) => void; max?: string; presets?: { label: string; range: DateRange }[] }) => {
  const p = usePopover(presets.length ? 440 : 290, 400);
  const [draft, setDraft] = useState<Partial<DateRange> | null>(null);
  const cur = draft ?? value;
  const pick = (k: string) => {
    if (!draft || draft.to || !draft.from) return setDraft({ from: k });
    const r = k < draft.from ? { from: k, to: draft.from } : { from: draft.from, to: k };
    setDraft(null); onChange(r); p.setOpen(false);
  };
  const text = value.from === value.to ? shortRu(value.from) : `${shortRu(value.from)} — ${shortRu(value.to)}`;
  return (
    <>
      <Trigger state={p} text={text} placeholder="Период" icon={<CalendarDays />} {...rest} />
      <PopoverPanel state={p} label="Выбор периода" className="flex gap-3">
        {presets.length > 0 && (
          <div className="flex w-36 shrink-0 flex-col gap-0.5 border-r border-border pr-3">
            {presets.map((x) => {
              const on = x.range.from === value.from && x.range.to === value.to;
              return <button key={x.label} type="button" onClick={() => { setDraft(null); onChange(x.range); p.setOpen(false); }} className={cn("rounded-md px-2.5 py-2 text-left text-sm transition-colors duration-fast hover:bg-surface", on && "bg-accent font-medium text-accent-foreground")}>{x.label}</button>;
            })}
          </div>
        )}
        <div>
          <p className="mb-2 text-xs text-muted-foreground">{draft?.from && !draft.to ? "Выберите конец периода" : "Выберите начало периода"}</p>
          <Calendar range={cur} max={max} onPick={pick} />
        </div>
      </PopoverPanel>
    </>
  );
};

const H = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, "0"));
const M = Array.from({ length: 12 }, (_, i) => String(i * 5).padStart(2, "0"));

/**
 * Колонка значений. Выбранное центрируется один раз — при открытии, и только прокруткой самой колонки
 * (без scrollIntoView, который двигает страницу). Дальше колонку крутит пользователь: перерисовки её не сбрасывают.
 */
const TimeCol = ({ list, cur, set, label }: { list: string[]; cur: string; set: (v: string) => void; label: string }) => {
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const el = ref.current, sel = el?.querySelector<HTMLElement>("[aria-selected=true]");
    if (el && sel) el.scrollTop = sel.offsetTop - el.clientHeight / 2 + sel.offsetHeight / 2;
  }, []);
  return (
    <div ref={ref} className="relative flex h-64 w-16 flex-col gap-0.5 overflow-y-auto overscroll-contain" role="listbox" aria-label={label}>
      {list.map((v) => (
        <button key={v} type="button" role="option" aria-selected={v === cur} onClick={() => set(v)}
          className={cn("relative shrink-0 rounded-md py-2 text-sm tabular-nums transition-colors duration-fast", v === cur ? "font-semibold text-primary-foreground" : "hover:bg-surface")}>
          {v === cur && <Dot className="rounded-md" />}<span className="relative">{v}</span>
        </button>
      ))}
    </div>
  );
};

/** Время ЧЧ:ММ: две колонки (часы и минуты с шагом 5), текущее значение видно сразу. */
export const TimePicker = ({ value, onChange, ...rest }: Aria & { value: string; onChange: (v: string) => void }) => {
  const p = usePopover(200, 300);
  const [h, m] = (value || "08:00").split(":");
  return (
    <>
      <Trigger state={p} text={value} placeholder="ЧЧ:ММ" icon={<Clock />} {...rest} />
      <PopoverPanel state={p} label="Выбор времени" className="flex gap-1">
        <TimeCol list={H} cur={h} set={(v) => onChange(`${v}:${m}`)} label="Часы" />
        <span className="w-px self-stretch bg-border" />
        <TimeCol list={M.includes(m) ? M : [...M, m].sort()} cur={m} set={(v) => { onChange(`${h}:${v}`); p.setOpen(false); }} label="Минуты" />
      </PopoverPanel>
    </>
  );
};
