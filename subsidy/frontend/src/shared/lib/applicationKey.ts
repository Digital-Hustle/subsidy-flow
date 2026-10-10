/**
 * Ключ заявки заявителя (ADR-008): приходит во фрагменте ссылки #k=… (не попадает в логи сервера и Referer),
 * сразу убирается из адресной строки и живёт в памяти вкладки (sessionStorage — переживает перезагрузку, но не вкладку).
 * В localStorage и query-строку не кладём.
 */
const PREFIX = "subsidy.key.";
const memory = new Map<string, string>();

export const applicationKey = {
  get(id: string): string | null {
    return memory.get(id) ?? sessionStorage.getItem(PREFIX + id);
  },
  set(id: string, key: string) {
    memory.set(id, key);
    sessionStorage.setItem(PREFIX + id, key);
  },
  clear(id: string) {
    memory.delete(id);
    sessionStorage.removeItem(PREFIX + id);
  },
  /** Забрать ключ из #k= и стереть его из адресной строки. */
  captureFromHash(id: string): string | null {
    const m = /(?:^#|&)k=([^&]+)/.exec(location.hash);
    if (m) {
      this.set(id, decodeURIComponent(m[1]));
      history.replaceState(history.state, "", location.pathname + location.search);
    }
    return this.get(id);
  },
};

/** Ссылка для возврата к заявке: ключ — во фрагменте. */
export const applicationLink = (id: string, key: string) => `${location.origin}/apply/${id}#k=${encodeURIComponent(key)}`;
