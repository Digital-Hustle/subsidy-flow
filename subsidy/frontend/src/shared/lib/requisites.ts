/**
 * Контрольные разряды реквизитов (аналог алгоритма Луна): подсказка сразу при вводе, решает сервер.
 * ИНН 10 (юрлицо) / 12 (ИП, физлицо) — взвешенная сумма mod 11 mod 10; ОГРН 13 — mod 11, ОГРНИП 15 — mod 13.
 */
const control = (v: string, w: number[]) => (w.reduce((s, k, i) => s + k * Number(v[i]), 0) % 11) % 10;

export const innValid = (v: string): boolean => {
  if (/^\d{10}$/.test(v)) return control(v, [2, 4, 10, 3, 5, 9, 4, 6, 8]) === Number(v[9]);
  if (/^\d{12}$/.test(v)) return control(v, [7, 2, 4, 10, 3, 5, 9, 4, 6, 8]) === Number(v[10]) && control(v, [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8]) === Number(v[11]);
  return false;
};

export const ogrnValid = (v: string): boolean => {
  if (!/^\d{13}$|^\d{15}$/.test(v)) return false;
  const body = BigInt(v.slice(0, -1));
  return Number((body % (v.length === 13 ? 11n : 13n)) % 10n) === Number(v[v.length - 1]);
};

/** Текст ошибки ИНН для поля (null — всё в порядке или пусто). */
export const innError = (v: string): string | null =>
  !v ? null : !/^\d{10}$|^\d{12}$/.test(v) ? "ИНН — 10 цифр у организации, 12 — у ИП" : !innValid(v) ? "Контрольные цифры ИНН не сходятся — проверьте номер" : null;

/** Текст ошибки ОГРН/ОГРНИП для поля. */
export const ogrnError = (v: string): string | null =>
  !v ? null : !/^\d{13}$|^\d{15}$/.test(v) ? "ОГРН — 13 цифр, ОГРНИП — 15" : !ogrnValid(v) ? "Контрольная цифра ОГРН не сходится — проверьте номер" : null;
