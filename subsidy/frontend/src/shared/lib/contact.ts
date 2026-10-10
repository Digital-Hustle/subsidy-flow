/** Контакт заявителя: телефон (10–15 цифр, 8… → +7…) или почта. Та же проверка — на сервере (package). */
export type Contact = { phone: string } | { email: string };

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const MAX_EMAIL = 120;

export const parseContact = (raw: string): Contact | null => {
  const v = raw.trim();
  if (!v) return null;
  if (v.includes("@")) return v.length <= MAX_EMAIL && EMAIL.test(v) ? { email: v.toLowerCase() } : null;
  if (/[^\d\s()+-]/.test(v)) return null;
  let d = v.replace(/\D/g, "");
  if (d.length === 11 && d.startsWith("8")) d = `7${d.slice(1)}`;
  if (d.length === 10 && d.startsWith("9")) d = `7${d}`;
  return d.length >= 11 && d.length <= 15 ? { phone: `+${d}` } : null;
};

export const contactError = (raw: string) =>
  !raw.trim() ? "Укажите телефон или почту" : raw.includes("@") ? "Проверьте адрес почты — например, name@mail.ru" : "Проверьте номер — например, +7 900 000-00-00";
