import type { DocumentCard, DocumentResult, FileCard } from "./types";

const BROKEN = new Set(["FAILED", "UNREADABLE"]);

/** B-08: насколько документ сомнителен — сломан > есть причина проверки > низкая уверенность вида. Больше — выше в списке. */
export const doubtOf = (d: DocumentCard, r?: DocumentResult): number => {
  const stage = r?.stage ?? d.stage;
  const reason = r?.reasonCode ?? d.reasonCode;
  const confidence = r?.typeConfidence ?? d.typeConfidence;
  let score = 0;
  if (BROKEN.has(stage)) score += 4;
  if (reason || stage === "NEEDS_REVIEW") score += 2;
  return score + (confidence === null || confidence === undefined ? 1 : 1 - confidence);
};

const fileDoubt = (f: FileCard, docs: Map<string, DocumentResult>) =>
  Math.max(BROKEN.has(f.stage) || f.reasonCode ? 4 : 0, ...f.documents.map((d) => doubtOf(d, docs.get(d.documentId))));

export type ArrangeOptions = { doubtFirst: boolean; type: string };

/** Порядок файлов и документов для списка: фильтр по виду ("" — все) и «сомнительные сверху». Исходный порядок — стабильный запасной. */
export const arrangeFiles = (files: FileCard[], docs: Map<string, DocumentResult>, { doubtFirst, type }: ArrangeOptions): FileCard[] => {
  const typeOf = (d: DocumentCard) => docs.get(d.documentId)?.type ?? d.type ?? "";
  let out = files.map((f) => ({ ...f, documents: type ? f.documents.filter((d) => typeOf(d) === type) : [...f.documents] }));
  if (type) out = out.filter((f) => f.documents.length > 0);
  if (doubtFirst) {
    out.forEach((f) => f.documents.sort((a, b) => doubtOf(b, docs.get(b.documentId)) - doubtOf(a, docs.get(a.documentId))));
    out.sort((a, b) => fileDoubt(b, docs) - fileDoubt(a, docs));
  }
  return out;
};

/** Сосед по видимому порядку для горячих клавиш: step = 1 вниз, -1 вверх; без выбора — первый. */
export const neighbourDoc = (files: FileCard[], selected: string | null, step: 1 | -1): string | null => {
  const ids = files.flatMap((f) => f.documents.map((d) => d.documentId));
  if (!ids.length) return null;
  const i = selected ? ids.indexOf(selected) : -1;
  if (i < 0) return ids[0];
  return ids[Math.min(ids.length - 1, Math.max(0, i + step))];
};