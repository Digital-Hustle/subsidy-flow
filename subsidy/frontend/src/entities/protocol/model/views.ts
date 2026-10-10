import type { Tone } from "@/shared/ui";
import type { RuleStatus } from "./types";

/** Подписи статусов правил (docs/RULES-CATALOG.md). Статус считает сервер — здесь только вид. */
export const RULE_STATUS: Record<RuleStatus, { label: string; tone: Tone; order: number }> = {
  FAILED: { label: "Ошибка", tone: "danger", order: 0 },
  ERROR: { label: "Сбой проверки", tone: "danger", order: 1 },
  DATA_MISSING: { label: "Нет данных", tone: "warning", order: 2 },
  WARNING: { label: "Предупреждение", tone: "warning", order: 3 },
  PASSED: { label: "Пройдено", tone: "success", order: 4 },
  NOT_APPLICABLE: { label: "Не применяется", tone: "neutral", order: 5 },
};
export const ruleView = (s: string) => RULE_STATUS[s as RuleStatus] ?? { label: s, tone: "neutral" as Tone, order: 9 };

export const CHECK_STATUS: Record<string, { label: string; tone: Tone }> = {
  OK: { label: "есть", tone: "success" },
  MISSING: { label: "не хватает", tone: "danger" },
  NOT_APPLICABLE: { label: "не требуется", tone: "neutral" },
};

export const HISTORY_ACTION: Record<string, string> = {
  PACKAGE_CREATED: "Создан пакет",
  PROTOCOL_CREATED: "Сформирован протокол",
  FIELD_CHANGED: "Исправлено поле",
  PACKAGE_DECIDED: "Принято решение",
};
