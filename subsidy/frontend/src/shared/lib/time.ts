const pad = (n: number) => String(n).padStart(2, "0");

export const todayKey = (d = new Date()) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const dayKey = (ts: number) => todayKey(new Date(ts));
export const hhmm = (ts: number) => { const d = new Date(ts); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
export const hhmmss = (ts: number) => { const d = new Date(ts); return `${hhmm(ts)}:${pad(d.getSeconds())}`; };
export const dateRu = (ts: number) => new Date(ts).toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
export const weekdayRu = (ts: number) => new Date(ts).toLocaleDateString("ru-RU", { weekday: "short", day: "numeric", month: "short" });

/** "08:30" + день → timestamp */
export const atTime = (day: string, time: string) => {
  const [y, m, d] = day.split("-").map(Number);
  const [h, mi] = time.split(":").map(Number);
  return new Date(y, m - 1, d, h, mi).getTime();
};

export const durationRu = (ms: number) => {
  const totalMin = Math.max(0, Math.round(ms / 60000));
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  return h ? `${h} ч ${pad(m)} мин` : `${m} мин`;
};

export const agoRu = (ts: number, now = Date.now()) => {
  const s = Math.round((now - ts) / 1000);
  if (s < 10) return "только что";
  if (s < 60) return `${s} с назад`;
  if (s < 3600) return `${Math.floor(s / 60)} мин назад`;
  if (s < 86400) return `${Math.floor(s / 3600)} ч назад`;
  return dateRu(ts);
};

export const plural = (n: number, one: string, few: string, many: string) => {
  const a = Math.abs(n) % 100, b = a % 10;
  if (a > 10 && a < 20) return many;
  if (b > 1 && b < 5) return few;
  if (b === 1) return one;
  return many;
};
