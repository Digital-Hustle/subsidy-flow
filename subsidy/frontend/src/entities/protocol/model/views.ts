import type { Tone } from "@/shared/ui";

/** Подписи статусов правил (docs/RULES-CATALOG.md). Статус считает сервер — здесь только вид. */
const RULE_STATUS: Record<string, { label: string; tone: Tone; order: number }> = {
  FAILED: { label: "Ошибка", tone: "danger", order: 0 },
  ERROR: { label: "Сбой проверки", tone: "danger", order: 1 },
  DATA_MISSING: { label: "Нет данных", tone: "warning", order: 2 },
  WARNING: { label: "Предупреждение", tone: "warning", order: 3 },
  PASSED: { label: "Пройдено", tone: "success", order: 4 },
  NOT_APPLICABLE: { label: "Не применяется", tone: "neutral", order: 5 },
};
export const ruleView = (s?: string | null) => RULE_STATUS[s ?? ""] ?? { label: s ?? "—", tone: "neutral" as Tone, order: 9 };
export const isProblem = (s?: string | null) => ruleView(s).order < RULE_STATUS.PASSED.order;

/** Названия правил — для статистики, где приходит только код. */
const RULE_TITLE: Record<string, string> = {
  MIN_FIELDS: "Минимум полей вычитан",
  INN_FORMAT: "ИНН — 10 или 12 цифр",
  INN_SAME: "ИНН одинаков во всех документах",
  DATE_NOT_FUTURE: "Дата документа не в будущем",
  NOT_EXPIRED: "Срок действия не истёк",
  OWN_FUNDS_SHARE: "Собственные средства не менее 50 % расходов",
  REQUEST_MATCH: "Сумма в заявлении совпадает с обоснованием",
  CADASTRAL_FORMAT: "Кадастровый номер в принятом формате",
  PRESENTATION_NOT_EMPTY: "Презентация не пустая",
  VISUAL_SIGNATURE: "Подпись видна на документе",
};
export const ruleTitle = (code: string) => RULE_TITLE[code] ?? code;

export const CHECKLIST_STATUS: Record<string, { label: string; tone: Tone }> = {
  OK: { label: "есть", tone: "success" },
  MISSING: { label: "не хватает", tone: "danger" },
  NOT_APPLICABLE: { label: "не требуется", tone: "neutral" },
};

const PROTOCOL: Record<string, { label: string; tone: Tone }> = {
  COMPLETE: { label: "Комплект полный", tone: "success" },
  INCOMPLETE: { label: "Не хватает документов", tone: "danger" },
  NEEDS_REVIEW: { label: "Нужна ручная проверка", tone: "warning" },
};
export const protocolView = (s?: string | null) => PROTOCOL[s ?? ""] ?? { label: s ?? "Проверка не завершена", tone: "neutral" as Tone };

const ORG_ACTION: Record<string, string> = {
  PACKAGE_CREATED: "Создан пакет",
  PROTOCOL_CREATED: "Сформирован протокол",
  FIELD_CHANGED: "Исправлено поле",
  PACKAGE_DECIDED: "Принято решение",
};
export const orgActionLabel = (a?: string | null) => ORG_ACTION[a ?? ""] ?? a ?? "—";
