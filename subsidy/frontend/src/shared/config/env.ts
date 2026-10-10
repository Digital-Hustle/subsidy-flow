/**
 * Настройки стенда. Адрес API можно сменить без пересборки: `/config.js` рядом со статикой
 * (в образе — `/srv/config.js`, в compose его можно подменить томом) задаёт `window.SUBSIDY_CONFIG`.
 * Пусто — API того же origin через Caddy (обычный стенд).
 */
type RuntimeConfig = { apiBase?: string };
const runtime = (window as unknown as { SUBSIDY_CONFIG?: RuntimeConfig }).SUBSIDY_CONFIG ?? {};
const base = (runtime.apiBase ?? (import.meta.env.VITE_API_BASE as string | undefined) ?? "").trim().replace(/\/+$/, "");

export const env = {
  /** Префикс всех запросов: "" — тот же origin, иначе, например, https://subsidy.example.ru */
  apiBase: base,
  /** API на другом хосте: cookie сессии нужно слать явно. */
  crossOrigin: base !== "" && !base.startsWith(location.origin),
} as const;
