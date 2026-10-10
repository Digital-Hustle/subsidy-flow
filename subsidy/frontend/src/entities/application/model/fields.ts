import type { DocumentCard, FieldCard, FieldCheck, FileCard, PackageRs, Card } from "./types";

/** Подписи полей документов (docs/DOC-TYPES.md): специалист видит русские названия, а не ключи схемы. */
const FIELD: Record<string, string> = {
  orgName: "Наименование организации", inn: "ИНН", orgInn: "ИНН организации", ogrn: "ОГРН / ОГРНИП", projectName: "Наименование проекта",
  requestedAmount: "Запрашиваемая сумма", totalCost: "Общая стоимость проекта", ownFunds: "Собственные средства",
  date: "Дата", signerName: "Подписант", fullName: "ФИО", passport: "Паспортные данные", number: "Номер",
  issuer: "Орган сертификации", issueDate: "Дата выдачи", validUntil: "Действует до", description: "Описание",
  items: "Статьи расходов", name: "Наименование", qty: "Количество", price: "Цена", amount: "Сумма", bankName: "Банк",
  account: "Номер счёта", bik: "БИК", balance: "Остаток", cadastralNumber: "Кадастровый номер", area: "Площадь",
  purpose: "Назначение", address: "Адрес", rightHolder: "Правообладатель", guarantorName: "Организация-гарант", summary: "Описание",
};
export const fieldLabel = (name: string) => FIELD[name] ?? name;

/** Значение поля строкой: деньги и даты приходят строками и показываются как есть. */
export const fieldText = (v: unknown): string => {
  if (v === null || v === undefined || v === "") return "";
  if (typeof v === "string" || typeof v === "number") return String(v);
  if (typeof v === "boolean") return v ? "да" : "нет";
  if (Array.isArray(v)) return v.map((x) => (x && typeof x === "object" ? Object.values(x as Record<string, unknown>).filter((y) => y != null && y !== "").join(" · ") : String(x))).join("\n");
  return JSON.stringify(v);
};

const LOW_CONFIDENCE = 0.85;
const BAD_CHECK = new Set(["FAILED", "WARNING", "ERROR"]);
const BROKEN = new Set(["FAILED", "UNREADABLE", "NEEDS_REVIEW", "WAITING_MODEL"]);

type RawField = { name?: string; value?: unknown; raw?: string | null; confidence?: number | null; checks?: FieldCheck[] | null; page?: number | null; bbox?: number[] | null };
/** Результат docproc по документу (GET /docproc/packages/{id}/documents). */
export type DocResult = {
  documentId?: string; fileId?: string; stage?: string; reasonCode?: string; type?: string; typeConfidence?: number;
  evidence?: string; source?: string; fields?: unknown; model?: string; promptVersion?: string;
};

const toFields = (raw: unknown): FieldCard[] => {
  const list: RawField[] = Array.isArray(raw) ? (raw as RawField[])
    : raw && typeof raw === "object" ? Object.entries(raw as Record<string, unknown>).map(([name, v]) => (v && typeof v === "object" && !Array.isArray(v) && "value" in v ? { name, ...(v as RawField) } : { name, value: v })) : [];
  const out = list.filter((f) => f.name).map((f): FieldCard => {
    const checks = f.checks ?? [];
    const value = fieldText(f.value);
    const attention = checks.some((c) => BAD_CHECK.has(c.status)) || (f.confidence != null && f.confidence < LOW_CONFIDENCE) || !value;
    return { name: f.name!, title: fieldLabel(f.name!), value, raw: f.raw, confidence: f.confidence, checks, attention, page: f.page, bbox: f.bbox };
  });
  // «Перепроверить» — сверху, остальные в порядке схемы.
  return [...out.filter((f) => f.attention), ...out.filter((f) => !f.attention)];
};

/** Собрать карточку: файлы и документы пакета + результаты docproc. Файл без документов — отдельный пункт со стадией файла. */
export const buildCard = (p: PackageRs, results: DocResult[], typeName: (t?: string | null) => string | null): Card => {
  const byId = new Map(results.map((r) => [r.documentId, r]));
  const files: FileCard[] = p.files ?? [];
  const documents: DocumentCard[] = files.flatMap((f): DocumentCard[] => {
    const pages = Math.max(1, f.pages ?? 1);
    const docs = f.documents ?? [];
    if (!docs.length) {
      return [{ id: `file:${f.fileId}`, fileId: f.fileId, fileName: f.name, real: false, stage: f.stage, reasonCode: f.reasonCode, type: null, typeName: null,
        fromPage: 1, toPage: pages, files: [{ id: f.fileId, name: f.name, version: f.version, pages, offset: 0 }], fields: [],
        attention: BROKEN.has(f.stage ?? "") || !!f.reasonCode }];
    }
    return docs.map((d) => {
      const r = byId.get(d.documentId);
      const from = d.fromPage ?? 1;
      const to = Math.max(from, d.toPage ?? pages);
      const type = r?.type ?? d.type ?? null;
      const fields = toFields(r?.fields);
      const stage = r?.stage ?? d.stage ?? f.stage;
      const reasonCode = r?.reasonCode ?? d.reasonCode ?? null;
      return {
        id: d.documentId, fileId: f.fileId, fileName: f.name, real: true, type, typeName: typeName(type),
        typeConfidence: r?.typeConfidence ?? d.typeConfidence, source: r?.source ?? d.source, stage, reasonCode,
        evidence: r?.evidence, model: r?.model, promptVersion: r?.promptVersion, fromPage: from, toPage: to,
        files: [{ id: f.fileId, name: f.name, version: f.version, pages: to - from + 1, offset: from - 1 }], fields,
        attention: BROKEN.has(stage ?? "") || !!reasonCode || fields.some((x) => x.attention),
      };
    });
  });
  return {
    id: p.packageId, status: p.status, org: p.org ?? {}, program: p.program, assignee: p.assignee, source: p.source,
    submittedAt: p.submittedAt, updatedAt: p.updatedAt, contact: p.contact, requests: p.requests ?? [],
    summary: p.protocol ?? null, decision: p.decision ?? null, documents, files, application: p.application, applicationRevision: p.applicationRevision,
  };
};
