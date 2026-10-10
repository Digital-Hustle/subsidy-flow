import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation } from "react-router";
import { cn } from "@/shared/lib";
import { motionTokens } from "@/shared/config/motion";
import { TrackIndicator, useTrackIndicator } from "./TrackIndicator";

/**
 * Шапка как на sberbank.ru: белая пилюля 60 px с внутренним отступом 6 px, висит над мятным фоном.
 * Липнет с зазором сверху (без полос под вырезом), при прокрутке только усиливается тень — геометрия не меняется.
 * transform-gpu — свой слой композиции: в iOS Safari липкий блок с backdrop-blur иначе подрагивает при инерционной прокрутке.
 */
export const HeaderBar = ({ children, className, inner }: { children: React.ReactNode; className?: string; inner?: string }) => {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(scrollY > motionTokens.header.shrinkAt);
    on();
    addEventListener("scroll", on, { passive: true });
    return () => removeEventListener("scroll", on);
  }, []);
  return (
    <header className={cn("pointer-events-none sticky top-0 z-nav transform-gpu px-2 pt-safe sm:px-6", className)}>
      <div
        className={cn("pointer-events-auto mx-auto mt-2 flex h-15 max-w-7xl items-center gap-2 relative rounded-lg p-1.5 glass-round transition-shadow duration-base sm:mt-3 sm:gap-3", scrolled ? "shadow-float" : "shadow-card", inner)}>
        {children}
      </div>
    </header>
  );
};

/**
 * Горизонтальная прокрутка, как у плашек на sberbank.ru: колесо мыши листает вбок, дорожку можно тянуть зажатой кнопкой.
 * Тач — нативная прокрутка. После перетаскивания клик по разделу не срабатывает (чтобы не уйти случайно).
 */
export const useDragScroll = <T extends HTMLElement>() => {
  const ref = useRef<T>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    let down = false, moved = false, startX = 0, startLeft = 0;
    const onWheel = (e: WheelEvent) => {
      const max = el.scrollWidth - el.clientWidth;
      if (max <= 0 || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return;
      const next = Math.max(0, Math.min(max, el.scrollLeft + e.deltaY));
      if (next === el.scrollLeft) return;
      e.preventDefault();
      el.scrollLeft = next;
    };
    const onDown = (e: PointerEvent) => { if (e.pointerType !== "mouse" || e.button !== 0) return; down = true; moved = false; startX = e.clientX; startLeft = el.scrollLeft; };
    const onMove = (e: PointerEvent) => {
      if (!down) return;
      const dx = e.clientX - startX;
      if (!moved && Math.abs(dx) < 5) return;
      if (!moved) { moved = true; el.dataset.dragging = "true"; }
      el.scrollLeft = startLeft - dx;
    };
    const onUp = () => { down = false; delete el.dataset.dragging; };
    const onClick = (e: MouseEvent) => { if (moved) { e.preventDefault(); e.stopPropagation(); moved = false; } };
    const noDrag = (e: DragEvent) => e.preventDefault();
    el.addEventListener("wheel", onWheel, { passive: false });
    el.addEventListener("pointerdown", onDown);
    addEventListener("pointermove", onMove);
    addEventListener("pointerup", onUp);
    addEventListener("pointercancel", onUp);
    el.addEventListener("click", onClick, true);
    el.addEventListener("dragstart", noDrag);
    return () => {
      el.removeEventListener("wheel", onWheel); el.removeEventListener("pointerdown", onDown);
      removeEventListener("pointermove", onMove); removeEventListener("pointerup", onUp); removeEventListener("pointercancel", onUp);
      el.removeEventListener("click", onClick, true); el.removeEventListener("dragstart", noDrag);
    };
  }, []);
  return ref;
};

export type NavItem = { to: string; label: React.ReactNode; end?: boolean; icon?: React.ReactNode };

/** Дорожка разделов в шапке: подложка surface + белая «пилюля» активного раздела (TrackIndicator, не вылетает за края). */
export const NavTrack = ({ items, className, label = "Разделы" }: { items: NavItem[]; className?: string; label?: string }) => {
  const { pathname } = useLocation();
  const ind = useTrackIndicator(pathname);
  const scroller = useDragScroll<HTMLElement>();
  // Активный раздел всегда виден: при переходе дорожка докручивается к нему.
  useEffect(() => {
    const el = scroller.current;
    const a = el?.querySelector<HTMLElement>('[aria-current="page"]');
    if (!el || !a || el.scrollWidth <= el.clientWidth) return;
    const l = a.offsetLeft, r = l + a.offsetWidth;
    if (l < el.scrollLeft || r > el.scrollLeft + el.clientWidth) el.scrollTo({ left: l - (el.clientWidth - a.offsetWidth) / 2, behavior: "smooth" });
  }, [pathname, scroller]);
  return (
    <nav ref={scroller} aria-label={label} className={cn("scrollbar-none min-w-0 cursor-grab select-none overflow-x-auto overscroll-x-contain rounded-md bg-surface p-1 data-[dragging=true]:cursor-grabbing", className)}>
      <div ref={ind.ref} className="relative flex w-max">
        <TrackIndicator ind={ind} className="inset-y-0 rounded-sm bg-card shadow-xs dark:bg-border-strong" />
        {items.map(({ to, label, end, icon }) => (
          <NavLink key={to} to={to} end={end} draggable={false}
            className={({ isActive }) => cn("relative z-raised flex h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-sm px-3.5 text-sm font-medium outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring xl:px-4 [&_svg]:size-4",
              isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground")}>
            {icon}{label}
          </NavLink>
        ))}
      </div>
    </nav>
  );
};
