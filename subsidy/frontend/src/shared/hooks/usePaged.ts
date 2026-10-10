import { useCallback, useEffect, useRef, useState } from "react";

export type PageLike<T> = { items: T[]; nextCursor: string | null; total: number };
type Fetch<T> = (cursor: string | null, limit: number) => Promise<PageLike<T>>;
type State<T> = { items: T[]; next: string | null; total: number; loading: boolean; error: boolean; ready: boolean };

/**
 * Бесконечный список поверх постраничного API (ADR-039).
 * key — сериализованные фильтры: при смене список сбрасывается на первую страницу.
 * live — любое значение, меняющееся при обновлении данных: загруженное окно тихо перезапрашивается.
 * Ответы устаревших запросов отбрасываются, дубли при подгрузке убираются по id.
 */
export const usePaged = <T,>(fetch: Fetch<T>, key: string, opts: { pageSize?: number; live?: unknown; id?: (t: T) => string } = {}) => {
  const size = opts.pageSize ?? 40;
  const f = useRef(fetch);
  const idOf = useRef(opts.id);
  useEffect(() => { f.current = fetch; idOf.current = opts.id; });
  const [st, setSt] = useState<State<T>>({ items: [], next: null, total: 0, loading: true, error: false, ready: false });
  const req = useRef(0);
  const want = useRef(size);

  const reload = useCallback(async (silent: boolean) => {
    const id = ++req.current;
    if (!silent) setSt((s) => ({ ...s, loading: true, error: false }));
    try {
      const items: T[] = []; let cursor: string | null = null; let total = 0;
      do {
        const p: PageLike<T> = await f.current(cursor, Math.min(100, want.current - items.length));
        items.push(...p.items); total = p.total; cursor = p.nextCursor;
      } while (cursor && items.length < want.current);
      if (id === req.current) setSt({ items, next: cursor, total, loading: false, error: false, ready: true });
    } catch {
      if (id === req.current) setSt((s) => ({ ...s, loading: false, error: true, ready: true }));
    }
  }, []);

  useEffect(() => { want.current = size; void reload(false); }, [key, size, reload]);
  const first = useRef(true);
  useEffect(() => { if (first.current) { first.current = false; return; } void reload(true); }, [opts.live, reload]);

  const more = useCallback(async () => {
    if (st.loading) return;
    if (st.error) { void reload(false); return; }
    if (!st.next) return;
    const id = ++req.current;
    want.current = st.items.length + size;
    setSt((s) => ({ ...s, loading: true }));
    try {
      const p = await f.current(st.next, size);
      if (id !== req.current) return;
      setSt((s) => {
        const get = idOf.current;
        const seen = get ? new Set(s.items.map(get)) : null;
        const add = seen && get ? p.items.filter((x) => !seen.has(get(x))) : p.items;
        return { items: [...s.items, ...add], next: p.nextCursor, total: p.total, loading: false, error: false, ready: true };
      });
    } catch {
      if (id === req.current) setSt((s) => ({ ...s, loading: false, error: true }));
    }
  }, [st.loading, st.error, st.next, st.items.length, size, reload]);

  return { items: st.items, total: st.total, hasMore: !!st.next, loading: st.loading, error: st.error, ready: st.ready, more, retry: () => reload(false) };
};

/** Значение с задержкой — чтобы поиск не дёргал сервер на каждую букву. */
export const useDebounced = <T,>(value: T, ms = 250) => {
  const [v, setV] = useState(value);
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t); }, [value, ms]);
  return v;
};
