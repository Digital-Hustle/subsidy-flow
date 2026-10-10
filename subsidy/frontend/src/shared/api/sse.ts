import { useEffect, useRef, useState } from "react";
import { api, streamUrl, type Service } from "./http";

export type StreamState = "connecting" | "open" | "retrying";
const MAX_BACKOFF_MS = 30_000;
const BASE_BACKOFF_MS = 1_000;

/**
 * Поток событий сервера (ADR-009): одноразовый билет → EventSource(?ticket=) — заголовки в EventSource не передаются.
 * Обрыв → новый билет с экспоненциальной задержкой. После переподключения вызывается onEvent("reconnect"),
 * чтобы экран перезапросил данные (пропущенные события не теряются).
 */
export const useEventStream = (svc: Service, ticketPath: string | null, streamPath: string, events: string[], onEvent: (name: string, data: unknown) => void) => {
  const [state, setState] = useState<StreamState>("connecting");
  const cb = useRef(onEvent);
  useEffect(() => { cb.current = onEvent; });
  const names = events.join(",");
  useEffect(() => {
    if (!ticketPath) return;
    let es: EventSource | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let closed = false;
    const connect = async () => {
      try {
        const { ticket } = await api<{ ticket?: string }>(svc, ticketPath, { method: "GET" });
        if (closed || !ticket) return;
        const base = streamUrl(svc, streamPath);
        es = new EventSource(`${base}${base.includes("?") ? "&" : "?"}ticket=${encodeURIComponent(ticket)}`);
        es.onopen = () => {
          if (attempt > 0) cb.current("reconnect", null);
          attempt = 0;
          setState("open");
        };
        for (const n of names.split(",")) {
          es.addEventListener(n, (e) => {
            let data: unknown;
            try { data = JSON.parse((e as MessageEvent).data as string); } catch { data = null; }
            cb.current(n, data);
          });
        }
        es.onerror = () => { es?.close(); retry(); };
      } catch {
        retry();
      }
    };
    const retry = () => {
      if (closed) return;
      setState("retrying");
      attempt += 1;
      timer = setTimeout(connect, Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2 ** attempt));
    };
    void connect();
    return () => { closed = true; es?.close(); clearTimeout(timer); };
  }, [svc, ticketPath, streamPath, names]);
  return state;
};
