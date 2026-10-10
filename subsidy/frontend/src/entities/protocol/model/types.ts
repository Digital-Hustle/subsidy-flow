/** Типы ответов rules (docs/API.md §rules). Статусы и суммы считает сервер — клиент только показывает. */
export type RuleStatus =
  | "PASSED" | "FAILED" | "WARNING" | "DATA_MISSING" | "NOT_APPLICABLE" | "ERROR";
/** Итог протокола — вычисляет rules. */
export type ProtocolState =
  | "COMPLETE" | "INCOMPLETE" | "NEEDS_REVIEW";
export type Evidence = { documentId: string | null; field: string | null; page: number | null };
export type RuleResult = { rule: string; status: RuleStatus; message: string; evidence: Evidence[] | null; expected: string | null; actual: string | null };
export type ChecklistItem = { item: string; title: string; kind: string; types: string[]; condition: string | null; status: string; documents: string[] };
export type MissingItem = { item: string; type: string; reason: string };
export type Protocol = {
  protocolId: string; packageId: string; version: number; status: ProtocolState; programCode: string;
  programVersion: number; checklistVersion: number; rulesVersion: string;
  completeness: { percent: number; items: ChecklistItem[]; missing: MissingItem[]; request: string | null };
  results: RuleResult[]; failed: number; warnings: number; needsReview: number; createdAt: string;
};
export type ProtocolSummary = { protocolId: string; version: number; status: Protocol["status"]; completeness: number; failed: number; warnings: number; needsReview: number; createdAt: string };
export type HistoryEntry = { at: string; by: string | null; action: string; packageId: string | null; details: Record<string, unknown> | null };
export type RuleStat = { rule: string; count: number; share: number | string };
export type Stats = { items: RuleStat[]; packages: number; method: string };
export type Expiring = { packageId: string; inn: string; documentId: string; type: string; title: string; date: string; expiresAt: string; daysLeft: number };
export type ProgramChecklistItem = { item: string; title: string; kind: string; types: string[]; condition: string | null };
export type Program = { code: string; version: number; name: string; demo: boolean; params: Record<string, unknown>; checklist: ProgramChecklistItem[]; updatedBy: string | null; updatedAt: string };

/** RUL-18: итог пересборки проекции документов из docproc. */
export type RebuildResult = { packageId: string; documents: number; updated: number; removed: number; status: string };
