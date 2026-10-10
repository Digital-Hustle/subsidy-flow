// Живой фон из фирменных цветов на WebGL (ogl, ADR-036): течёт мягкий шум, как «северное сияние» в СберБанк Онлайн.
// Без WebGL — статичный градиент. При prefers-reduced-motion рисуется один кадр.
import { useEffect, useRef } from "react";
import { Renderer, Program, Mesh, Triangle, Color } from "ogl";
import { palette, motion as m } from "@/shared/config/tokens";
import { useIsDark } from "@/shared/hooks";
import { cn } from "@/shared/lib";

const vert = `attribute vec2 position; void main(){ gl_Position = vec4(position, 0.0, 1.0); }`;
const frag = `precision highp float;
uniform float uTime; uniform vec2 uRes; uniform vec3 uC1; uniform vec3 uC2; uniform vec3 uC3; uniform vec3 uBg; uniform float uIntensity; uniform float uBand;
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
float noise(vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.0-2.0*f);
  return mix(mix(hash(i),hash(i+vec2(1,0)),u.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y); }
float fbm(vec2 p){ float v=0.0, a=0.5; for(int i=0;i<5;i++){ v+=a*noise(p); p*=2.0; a*=0.5; } return v; }
void main(){
  vec2 uv = gl_FragCoord.xy / uRes; vec2 p = uv * vec2(uRes.x/uRes.y, 1.0);
  float t = uTime;
  float n1 = fbm(p * 1.4 + vec2(t, -t * 0.7));
  float n2 = fbm(p * 2.1 - vec2(t * 0.8, t * 0.4) + n1);
  vec3 col = mix(uC1, uC2, smoothstep(0.2, 0.8, n1));
  col = mix(col, uC3, smoothstep(0.45, 0.95, n2) * 0.8);
  float band = mix(1.0, smoothstep(0.0, 0.9, uv.y + n2 * 0.4 - 0.25), uBand);
  gl_FragColor = vec4(mix(uBg, col, band * uIntensity), 1.0);
}`;

const P = palette;
/** Палитры сияния: светлая — мята → бирюза → лайм на мятном фоне, тёмная — глубокий зелёный и бирюза на графите. */
const TONES = {
  light: { c1: P.sber.mint, c2: P.sber.cyan, c3: P.sber.lime, bg: P.mint.sber2 },
  dark: { c1: P.sber.green, c2: P.teal[500], c3: P.sber.ocean, bg: P.graphite[900] },
  kiosk: { c1: P.sber.green, c2: P.sber.sky, c3: P.sber.spring, bg: P.black },
} as const;

type Props = {
  className?: string;
  /** auto — по теме сайта */
  tone?: "auto" | keyof typeof TONES;
  intensity?: number;
  /** true — сияние сверху и гаснет книзу (герой), false — по всей площади */
  band?: boolean;
  /** fixed — фон всей страницы за контентом */
  fixed?: boolean;
};

/** Одна сила сияния на всех экранах (главная, пропуск, кабинет, киоск) — фон не «прыгает» при переходах. */
export const PAGE_AURORA = 0.6;
/** Экран киоска: тёмная палитра, та же сила на всех состояниях (ожидание, привязка, нет связи). */
export const KIOSK_AURORA = 0.9;

export const Aurora = ({ className, tone = "auto", intensity = PAGE_AURORA, band = true, fixed }: Props) => {
  const host = useRef<HTMLDivElement>(null);
  const dark = useIsDark();
  const key = tone === "auto" ? (dark ? "dark" : "light") : tone;
  useEffect(() => {
    const el = host.current;
    if (!el) return;
    let renderer: Renderer;
    try { renderer = new Renderer({ dpr: m.aurora.resolution, alpha: false, antialias: false }); } catch { return; }
    const gl = renderer.gl;
    el.appendChild(gl.canvas);
    gl.canvas.className = "absolute inset-0 size-full";
    const c = TONES[key];
    const program = new Program(gl, {
      vertex: vert, fragment: frag,
      uniforms: {
        uTime: { value: 0 }, uRes: { value: [1, 1] }, uIntensity: { value: intensity }, uBand: { value: band ? 1 : 0 },
        uC1: { value: new Color(c.c1) }, uC2: { value: new Color(c.c2) }, uC3: { value: new Color(c.c3) }, uBg: { value: new Color(c.bg) },
      },
    });
    const mesh = new Mesh(gl, { geometry: new Triangle(gl), program });
    const draw = (t: number) => { program.uniforms.uTime.value = (t / 1000) * m.aurora.speed; renderer.render({ scene: mesh }); };
    const resize = () => { renderer.setSize(el.clientWidth, el.clientHeight); program.uniforms.uRes.value = [gl.drawingBufferWidth, gl.drawingBufferHeight]; draw(performance.now()); };
    const ro = new ResizeObserver(resize);
    ro.observe(el);
    resize();
    const still = matchMedia("(prefers-reduced-motion: reduce)").matches;
    let raf = 0;
    const loop = (t: number) => { if (!document.hidden) draw(t); raf = requestAnimationFrame(loop); };
    if (!still) raf = requestAnimationFrame(loop);
    return () => { cancelAnimationFrame(raf); ro.disconnect(); gl.canvas.remove(); gl.getExtension("WEBGL_lose_context")?.loseContext(); };
  }, [key, intensity, band]);
  // fixed — высотой в «большой» экран (lvh): на iOS при скрытии адресной строки размер не меняется,
  // холст не пересоздаётся на каждом кадре прокрутки и не дёргает липкую шапку.
  return <div ref={host} aria-hidden className={cn("pointer-events-none -z-10 overflow-hidden bg-page", fixed ? "fixed inset-x-0 top-0 h-lvh" : "absolute inset-0", className)} />;
};
