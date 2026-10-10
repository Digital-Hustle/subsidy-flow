import { env } from "@/shared/config/env";
import { ApiError, networkError, toApiError } from "./problem";

export type Service = "package" | "docproc" | "rules" | "assistant";

/** Откуда брать заголовок Authorization: зона соискателя — ключ заявки, зона оператора — токен OIDC. */
type AuthSource = () => Promise<string | null> | string | null;
let authSource: AuthSource = () => null;
let onUnauthorized: () => void = () => {};

export const setAuth = (src: AuthSource, unauthorized: () => void = () => {}) => {
  authSource = src;
  onUnauthorized = unauthorized;
};

export type RequestOpts = {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  /** Запрос входа: без Authorization, но с httpOnly-cookie сессии (ADR-023). */
  session?: boolean;
};

const url = (svc: Service, path: string) => `${env.apiBase}/${svc}/api/v1${path}`;

const send = async (svc: Service, path: string, o: RequestOpts): Promise<Response> => {
  const headers: Record<string, string> = { Accept: "application/json", ...o.headers };
  if (!o.session) {
    const auth = await authSource();
    if (auth) headers.Authorization = auth;
  }
  let body: BodyInit | undefined;
  if (o.body instanceof FormData) body = o.body;
  else if (o.body !== undefined) { body = JSON.stringify(o.body); headers["Content-Type"] = "application/json"; }
  let res: Response;
  try {
    // shared/api — единственная точка сетевого доступа (rules.md §4); fetch нужен ради потоков и blob без axios
    // eslint-disable-next-line no-restricted-globals
    res = await fetch(url(svc, path), { method: o.method ?? "GET", headers, body, signal: o.signal, credentials: o.session ? (env.crossOrigin ? "include" : "same-origin") : "omit" });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw networkError();
  }
  if (res.status === 401 && !o.session) onUnauthorized();
  if (!res.ok) throw await toApiError(res);
  return res;
};

/** JSON-запрос к сервису через gateway: /<svc>/api/v1/… */
export const api = async <T,>(svc: Service, path: string, o: RequestOpts = {}): Promise<T> => {
  const res = await send(svc, path, o);
  if (res.status === 204 || res.headers.get("Content-Length") === "0") return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
};

/** Бинарный ответ (страница документа, выгрузка) — только через сервер с токеном, без публичных URL. */
export const apiBlob = async (svc: Service, path: string, signal?: AbortSignal): Promise<Blob> => {
  const res = await send(svc, path, { headers: { Accept: "*/*" }, signal });
  return res.blob();
};

/** Скачать файл из API: имя — из Content-Disposition или запасное. */
export const download = async (svc: Service, path: string, fallback: string) => {
  const res = await send(svc, path, { headers: { Accept: "*/*" } });
  const cd = res.headers.get("Content-Disposition") ?? "";
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  const name = m ? decodeURIComponent(m[1]) : fallback;
  const href = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement("a"), { href, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
};

export const streamUrl = url;
export { ApiError };
