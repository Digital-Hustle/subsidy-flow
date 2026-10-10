// QR со стилем: соседние модули сливаются в группы со скруглёнными краями (см. shared/lib/qrShape),
// глазки — скруглённые квадраты, в центре — знак «Документов». Тёмное на белом: сканируется камерой киоска и телефоном.
import { useId, useLayoutEffect, useMemo, useRef } from "react";
import { animate, useReducedMotion } from "motion/react";
import { palette as P } from "@/shared/config/tokens";
import { duration, ease } from "@/shared/config/motion";
import { bodyPath, cn, dotPath, finderPaths, qrMatrix, type QrMatrix } from "@/shared/lib";

const SPREAD = 0.6; // доля времени, за которую волна проходит от центра к краям
const clamp = (v: number) => Math.max(0, Math.min(1, v));
const outCubic = (t: number) => 1 - (1 - t) ** 3;

/** Задержка модуля: волна идёт от знака в центре к краям, лёгкий разброс делает её «живой». */
const delays = (n: number) => {
  const c = n / 2, max = Math.hypot(c, c);
  const d = new Float32Array(n * n);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const r = (Math.sin((x * 12.9898 + y * 78.233) * 43758.5453) + 1) % 1;
    d[y * n + x] = Math.min(1, (Math.hypot(x + 0.5 - c, y + 0.5 - c) / max) * 0.88 + r * 0.12);
  }
  return d;
};

/**
 * Кадр перестройки: модули, общие для двух кодов, стоят на месте; исчезающие сжимаются в точку,
 * новые вырастают с лёгким перелётом. Как только модуль встал — он вливается в общий путь со скруглениями.
 */
const frame = (a: QrMatrix, b: QrMatrix, del: Float32Array, p: number) => {
  const { n } = b;
  const merged = new Uint8Array(n * n);
  const dots: string[] = [];
  for (let i = 0; i < n * n; i++) {
    const was = a.on[i], will = b.on[i];
    if (was === will) { merged[i] = will; continue; }
    const t = clamp((p - del[i] * SPREAD) / (1 - SPREAD));
    const x = i % n, y = (i - x) / n;
    if (was) {
      if (t <= 0) merged[i] = 1;
      else if (t < 1) dots.push(dotPath(x, y, 1 - outCubic(t)));
    } else if (t >= 1) merged[i] = 1;
    else if (t > 0) dots.push(dotPath(x, y, outCubic(t) * (1 + 0.22 * Math.sin(Math.PI * t))));
  }
  return { body: bodyPath(n, merged, b.ex), dots: dots.join("") };
};

/** morph — при смене значения код перестраивается волной от центра, а не меняется целиком. */
export const StyledQr = ({ value, className, mark = true, label = "QR-код", morph = false }: { value: string; className?: string; mark?: boolean; label?: string; morph?: boolean }) => {
  const id = useId().replace(/[^a-zA-Z0-9]/g, "");
  const reduce = useReducedMotion();
  const m = useMemo(() => qrMatrix(value, { level: "M", holeRatio: mark ? 0.2 : 0 }), [value, mark]);
  const finders = useMemo(() => finderPaths(m.n), [m.n]);
  const body = useRef<SVGPathElement>(null);
  const dots = useRef<SVGPathElement>(null);
  const prev = useRef<QrMatrix | null>(null);
  const del = useMemo(() => delays(m.n), [m.n]);

  // Путь тела ведётся вручную: во время перестройки он меняется каждый кадр, React его не трогает.
  useLayoutEffect(() => {
    const from = prev.current;
    prev.current = m;
    const set = (b: string, d = "") => { body.current?.setAttribute("d", b); dots.current?.setAttribute("d", d); };
    if (!morph || reduce || !from || from.n !== m.n) { set(bodyPath(m.n, m.on, m.ex)); return; }
    const ctl = animate(0, 1, {
      duration: duration.slow * 2.2, ease: ease.inOut,
      onUpdate: (p) => { const f = frame(from, m, del, p); set(f.body, f.dots); },
      onComplete: () => set(bodyPath(m.n, m.on, m.ex)),
    });
    return () => ctl.stop();
  }, [m, morph, reduce, del]);

  const n = m.n, c = n / 2, s = m.hole - 1.2;
  return (
    <svg viewBox={`0 0 ${n} ${n}`} role="img" aria-label={label} className={cn("block aspect-square", className)}>
      <defs>
        <linearGradient id={`b${id}`} x1="0" y1="0" x2="1" y2="1" gradientUnits="userSpaceOnUse" gradientTransform={`scale(${n})`}>
          <stop offset="0" stopColor={P.green[900]} /><stop offset="1" stopColor={P.sber.ocean} />
        </linearGradient>
        <linearGradient id={`m${id}`} x1="0" y1="1" x2="1" y2="0">
          <stop offset="0" stopColor={P.sber.spring} /><stop offset="0.45" stopColor={P.sber.green} /><stop offset="1" stopColor={P.sber.lagoon} />
        </linearGradient>
      </defs>
      <path ref={body} fill={`url(#b${id})`} />
      <path ref={dots} fill={`url(#b${id})`} />
      <path d={finders.ring} fill={P.green[800]} fillRule="evenodd" />
      <path d={finders.eye} fill={P.green[800]} />
      {mark && m.hole > 0 && (
        <g>
          <rect x={c - s / 2} y={c - s / 2} width={s} height={s} rx={s * 0.32} fill={`url(#m${id})`} />
          <path d={`M${c - s * 0.24} ${c + s * 0.02}l${s * 0.15} ${s * 0.15}l${s * 0.3} -${s * 0.32}`} fill="none" stroke={P.white} strokeWidth={s * 0.12} strokeLinecap="round" strokeLinejoin="round" />
        </g>
      )}
    </svg>
  );
};
