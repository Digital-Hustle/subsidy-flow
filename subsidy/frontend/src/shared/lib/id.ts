const ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

export const randomId = (prefix: string, len = 8) => {
  const bytes = crypto.getRandomValues(new Uint8Array(len));
  return `${prefix}_${Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join("").toLowerCase()}`;
};

/** Код приглашения: 6 символов без похожих (0/O, 1/I). */
export const inviteCode = () => Array.from(crypto.getRandomValues(new Uint8Array(6)), (b) => ALPHABET[b % ALPHABET.length]).join("");

/** Детерминированный ГПСЧ для сида демо-данных. */
export const mulberry32 = (seed: number) => () => {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
