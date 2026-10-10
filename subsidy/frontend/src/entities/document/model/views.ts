import type { Tone } from "@/shared/ui";

/** Стадии файла/документа — docs/API.md §Стадии (заявителю — простыми словами). */
const STAGES: Record<string, { label: string; tone: Tone; done?: boolean; problem?: boolean }> = {
  RECEIVED: { label: "Файл принят", tone: "info" },
  QUEUED: { label: "В очереди на разбор", tone: "info" },
  EXTRACTING_TEXT: { label: "Читаем текст", tone: "info" },
  CLASSIFYING: { label: "Определяем, какие документы в файле", tone: "info" },
  SEGMENTED: { label: "Документы найдены", tone: "info" },
  EXTRACTING_FIELDS: { label: "Извлекаем данные", tone: "info" },
  VALIDATING: { label: "Проверяем реквизиты", tone: "info" },
  PARSED: { label: "Разобран", tone: "success", done: true },
  NEEDS_REVIEW: { label: "Разобран, есть что проверить", tone: "warning", done: true },
  UNREADABLE: { label: "Не удалось прочитать", tone: "danger", done: true, problem: true },
  WAITING_MODEL: { label: "Разбор приостановлен, продолжим автоматически", tone: "warning" },
  FAILED: { label: "Не удалось разобрать", tone: "danger", done: true, problem: true },
};
const ORDER = ["RECEIVED", "QUEUED", "EXTRACTING_TEXT", "CLASSIFYING", "EXTRACTING_FIELDS", "VALIDATING", "PARSED"];

export const stageView = (stage?: string | null) => {
  const s = STAGES[stage ?? ""] ?? { label: stage ?? "—", tone: "neutral" as Tone };
  const i = ORDER.indexOf(stage === "SEGMENTED" ? "CLASSIFYING" : stage ?? "");
  return { ...s, done: !!s.done, problem: !!s.problem, progress: s.done ? 1 : i < 0 ? 0.5 : (i + 1) / ORDER.length };
};

/** Причины непригодности и замечания — docs/API.md §Причины. Параметры подставляются в текст. */
const REASONS: Record<string, [string, string?]> = {
  IMAGE_BLURRY: ["Скан размыт", "Отсканируйте документ заново в хорошем качестве"],
  IMAGE_TOO_DARK: ["Скан слишком тёмный", "Отсканируйте документ при хорошем освещении"],
  IMAGE_CUT_OFF: ["Документ обрезан", "Все страницы должны быть видны целиком"],
  TEXT_NOT_FOUND: ["Не удалось найти текст", "Загрузите PDF с текстовым слоем или более чёткий скан"],
  TYPE_UNKNOWN: ["Не удалось уверенно определить вид документа", "Специалист выберет вид вручную"],
  TYPE_OTHER: ["Документ не входит в перечень", "Проверьте, тот ли файл загружен"],
  CHECKSUM_FAILED: ["Реквизит не прошёл проверку контрольной суммы", "Проверьте ИНН/ОГРН в документе"],
  DUPLICATE_DOCUMENT: ["Такой документ уже есть в заявке", "Загрузите другой документ"],
  MODEL_UNAVAILABLE: ["Модель недоступна", "Продолжим автоматически"],
  MODEL_INVALID_OUTPUT: ["Модель вернула ответ не по схеме", "Специалист проверит вручную"],
  FIELDS_TO_CHECK: ["Есть поля, которые стоит проверить"],
  PDF_ONLY: ["Принимаются только PDF", "Сохраните документ в PDF и загрузите снова"],
};

const fill = (t: string, p: Record<string, unknown>) => t.replace(/\{(\w+)\}/g, (_, k: string) => (p[k] == null ? "" : String(p[k])));

/** Названия видов (docproc /doc-types) — когда документа такого вида в заявке ещё нет и typeName взять неоткуда. */
const TYPE_TITLE: Record<string, string> = {
  APPLICATION: "Заявление на субсидию", PD_CONSENT: "Согласие на обработку ПД", CERTIFICATE: "Сертификат",
  PRESENTATION: "Презентация проекта", COST_JUSTIFICATION: "Обоснование затрат", BANK_STATEMENT: "Банковская выписка",
  PROPERTY_DOC: "Документ на помещение", GUARANTEE_LETTER: "Гарантийное письмо", GIMS_DECLARATION: "Декларация ГИМС", OTHER: "Другой документ",
};
export const typeTitle = (t?: string | null) => (t ? TYPE_TITLE[t] ?? t : "документ");

export const reasonView = (code?: string | null, params: Record<string, unknown> = {}) => {
  if (!code) return null;
  const [text, hint] = REASONS[code] ?? [`Сервер сообщил о проблеме обработки (${code})`];
  return { code, text: fill(text, params), hint: hint && fill(hint, params) };
};

/** Проверки поля (docproc checks[].code) — короткие подписи для специалиста. */
const CHECKS: Record<string, string> = {
  CHECKSUM_FAILED: "контрольная сумма",
  FORMAT_INVALID: "формат",
  LOW_CONFIDENCE: "модель не уверена",
  INN_CHECKSUM: "контрольная сумма ИНН",
  OGRN_CHECKSUM: "контрольная сумма ОГРН",
  BIK_FORMAT: "формат БИК",
  ACCOUNT_CHECKSUM: "ключ счёта",
  DATE_FORMAT: "формат даты",
  NOT_IN_FUTURE: "дата не в будущем",
  NOT_EXPIRED: "срок не истёк",
  MONEY_FORMAT: "формат суммы",
  CADASTRAL_FORMAT: "формат кадастрового номера",
  FORMAT: "формат",
};
export const checkLabel = (code: string) => CHECKS[code] ?? code.toLowerCase().replace(/_/g, " ");
export const checkOk = (status?: string | null) => status === "OK" || status === "PASSED";

/** Уверенность 0..1 → тон: ниже порога — «перепроверить». */
export const confidenceTone = (c?: number | null): Tone => (c == null ? "neutral" : c >= 0.85 ? "success" : c >= 0.6 ? "warning" : "danger");
export const percent = (c?: number | null) => (c == null ? "—" : `${Math.round(c * 100)}%`);
