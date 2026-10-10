import type { Tone } from "@/shared/ui";

/** Статусы пакета (docs/API.md): заявитель собирает → отправил → разбор → проверен → нужны исправления → решение. */
const STATUS: Record<string, { label: string; tone: Tone; applicant?: string }> = {
  DRAFT: { label: "Собирается", tone: "neutral", applicant: "Черновик" },
  COLLECTING: { label: "Собирается", tone: "neutral" },
  SUBMITTED: { label: "Ждёт проверки", tone: "info", applicant: "Отправлена" },
  PROCESSING: { label: "Разбирается", tone: "info", applicant: "Проверяем" },
  CHECKED: { label: "Проверен", tone: "warning", applicant: "У специалиста" },
  NEEDS_FIX: { label: "Ждём исправлений", tone: "warning", applicant: "Нужны исправления" },
  DECIDED: { label: "Решение принято", tone: "success", applicant: "Решение принято" },
};
export const statusView = (s?: string | null) => STATUS[s ?? ""] ?? { label: s ?? "—", tone: "neutral" as Tone };
export const applicantStatusView = (s?: string | null) => { const v = statusView(s); return { ...v, label: (v as { applicant?: string }).applicant ?? v.label }; };

const DECISION: Record<string, { label: string; tone: Tone }> = {
  APPROVE: { label: "Одобрено", tone: "success" },
  REJECT: { label: "Отказано", tone: "danger" },
  RETURN: { label: "Возвращено на доработку", tone: "warning" },
};
export const decisionView = (d?: string | null) => DECISION[d ?? ""] ?? { label: d ?? "—", tone: "neutral" as Tone };

const ACTIONS: Record<string, string> = {
  PACKAGE_CREATED: "Создан пакет",
  APPLICATION_CREATED: "Заявитель начал заявку",
  APPLICATION_UPDATED: "Изменена анкета",
  FILE_UPLOADED: "Загружен файл",
  DOCUMENT_UPLOADED: "Загружен документ",
  FILE_REMOVED: "Файл удалён",
  DOCUMENT_REMOVED: "Документ удалён",
  APPLICATION_DELETED: "Заявка удалена заявителем, данные стёрты",
  DOCUMENT_PARSED: "Документ разобран",
  PROTOCOL_CREATED: "Сформирован протокол",
  SUBMITTED: "Заявка отправлена",
  ASSIGNED: "Назначена специалисту",
  FIELD_CHANGED: "Исправлено поле",
  FIELD_CORRECTED: "Исправлено поле",
  TYPE_CORRECTED: "Изменён вид документа",
  REPROCESS_REQUESTED: "Запрошен повторный разбор",
  FIX_REQUESTED: "Запрошены исправления у заявителя",
  RESUBMIT_REQUESTED: "Запрошены исправления у заявителя",
  PACKAGE_DECIDED: "Решение по заявке",
  DECIDED: "Решение по заявке",
  EXPORTED: "Выгружен протокол",
};
export const actionLabel = (a?: string | null) => ACTIONS[a ?? ""] ?? a ?? "—";

const SOURCES: Record<string, string> = { APPLICANT: "заявитель", SPECIALIST: "специалист", OPERATOR: "специалист", MODEL: "модель", SYSTEM: "система" };
export const sourceLabel = (s?: string | null) => SOURCES[s ?? ""] ?? s ?? "";

/** Секунды ожидания → «2 ч 15 мин». */
export const waitingRu = (sec?: number | null) => {
  if (sec == null) return "—";
  const m = Math.floor(sec / 60);
  if (m < 1) return "меньше минуты";
  if (m < 60) return `${m} мин`;
  const h = Math.floor(m / 60);
  if (h < 48) return `${h} ч ${m % 60} мин`;
  return `${Math.floor(h / 24)} дн`;
};

export const dateTimeRu = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—";
export const dateRuFull = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" }) : "—";
/** Деньги приходят строкой "12345.67" — показываем с разрядами, без потери точности. */
export const moneyRu = (v?: string | null) => {
  if (!v) return "—";
  const [i, f] = v.split(".");
  return `${i.replace(/\B(?=(\d{3})+(?!\d))/g, " ")}${f && f !== "00" ? `,${f}` : ""} ₽`;
};

export const ADDRESS_PURPOSE: Record<string, string> = { LEGAL: "Юридический адрес", ACTUAL: "Фактический адрес", PROJECT: "Место реализации проекта" };
export const RIGHT_TYPE: Record<string, string> = { OWNERSHIP: "Собственность", LEASE: "Аренда", USE: "Безвозмездное пользование" };
