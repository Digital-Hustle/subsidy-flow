import { api, ApiError, download } from "@/shared/api";
import { hasApplicationDetails, unsavedApplicationFields } from "../model/application-form";
import { buildCard, type DocResult } from "../model/fields";
import type { Card, DocType, Expiring, HistoryEntry, ModelStatus, PackageRs, QueuePage, ApplicationData, UploadResult } from "../model/types";

export type QueueFilter = { status?: string; q?: string; assignee?: string; sort?: string; program?: string; inn?: string };

const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

/** 404/405 — эндпоинт ещё не выкатили (новый контракт зоны /operator): показываем пусто, а не ошибку. */
const optional = async <T,>(p: Promise<T>, fallback: T): Promise<T> => {
  try { return await p; } catch (e) { if (e instanceof ApiError && (e.status === 404 || e.status === 405 || e.status === 501)) return fallback; throw e; }
};

const verifyApplication = async (id: string, expected: ApplicationData) => {
  const card = await api<PackageRs>("package", `/packages/${id}`);
  const missing = unsavedApplicationFields(expected, card.application);
  if (missing.length) throw new ApiError(409, "APPLICATION_NOT_SAVED", "Сервер сохранил не все сведения заявки", "Введённые значения оставлены в форме. Обновите API перед повторным сохранением.", undefined, { packageId: id, errors: missing.map((field) => ({ field: `application.${field}`, message: "Сервер не сохранил это поле" })) });
};

let typesCache: Promise<DocType[]> | null = null;
const types = () => {
  typesCache ??= api<{ items?: DocType[] }>("docproc", "/doc-types").then((r) => r.items ?? []).catch((e: unknown) => { typesCache = null; throw e; });
  return typesCache;
};

/**
 * Рабочее место специалиста. Очередь и пакет — package; поля и виды — docproc; протокол, правки, отчёты — rules.
 * Карточку собираем здесь, чтобы страницы работали с одной моделью.
 */
export const reviewApi = {
  create: async (orgInn: string, programCode: string, application: ApplicationData, key: string) => {
    const created = await api<{ packageId: string }>("package", "/packages", { method: "POST", body: { orgInn, programCode, ...(hasApplicationDetails(application) ? { application } : {}) }, headers: { "Idempotency-Key": key } });
    if (hasApplicationDetails(application)) await verifyApplication(created.packageId, application);
    return created;
  },
  updateApplication: async (id: string, expectedRevision: number, application: ApplicationData) => {
    await api<unknown>("package", `/packages/${id}/application`, { method: "PUT", body: { expectedRevision, application } });
    await verifyApplication(id, application);
  },
  upload: (id: string, files: File[]) => {
    const body = new FormData(); files.forEach((file) => body.append("files", file, file.name));
    return api<UploadResult>("package", `/packages/${id}/files`, { method: "POST", body });
  },
  replace: (id: string, fileId: string, file: File) => {
    const body = new FormData(); body.append("file", file, file.name);
    return api<UploadResult>("package", `/packages/${id}/files/${fileId}/versions`, { method: "POST", body });
  },
  downloadOriginal: (fileId: string, version: number, name: string) => download("package", `/files/${fileId}/content?version=${version}`, name),
  queue: (f: QueueFilter, cursor: string | null, limit = 30) => api<QueuePage>("package", `/packages${qs({ ...f, cursor, limit })}`),
  count: (status: string) => api<QueuePage>("package", `/packages${qs({ status, limit: 1 })}`).then((r) => r.page?.total ?? r.items?.length ?? 0),
  /** Следующая свободная заявка; пока на стенде нет /packages/next — самая давняя проверенная из очереди. */
  next: async () => {
    try { return await api<{ packageId?: string } | undefined>("package", "/packages/next", { method: "POST" }); } catch (e) {
      if (!(e instanceof ApiError && (e.status === 404 || e.status === 405))) throw e;
      const r = await api<QueuePage>("package", `/packages${qs({ status: "CHECKED", sort: "waiting", limit: 1 })}`);
      return { packageId: r.items?.[0]?.id };
    }
  },
  assign: (id: string, force = false) => api<void>("package", `/packages/${id}/assign${force ? "?force=true" : ""}`, { method: "POST" }),
  card: async (id: string): Promise<Card> => {
    const [p, docs, t] = await Promise.all([
      api<PackageRs>("package", `/packages/${id}`),
      optional(api<{ items?: DocResult[] }>("docproc", `/packages/${id}/documents`), { items: [] }),
      types().catch(() => [] as DocType[]),
    ]);
    const names = new Map(t.map((x) => [x.code, x.name ?? x.code]));
    return buildCard(p, docs.items ?? [], (c) => (c ? names.get(c) ?? null : null));
  },
  history: (id: string) => optional(api<{ items?: HistoryEntry[] }>("package", `/packages/${id}/history`), { items: undefined }),
  request: (id: string, items: { documentId?: string; type?: string; reason: string }[]) =>
    api<void>("package", `/packages/${id}/request`, { method: "POST", body: { items } }),
  decide: (id: string, decision: "APPROVE" | "REJECT" | "RETURN", comment?: string) =>
    api<void>("package", `/packages/${id}/decision`, { method: "POST", body: { decision, comment: comment ?? null } }),
  exportProtocol: (id: string, format: "docx" | "xlsx" | "pdf") => download("rules", `/packages/${id}/protocol/export?format=${format}`, `protocol-${id.slice(0, 8)}.${format}`),
  correct: (packageId: string, documentId: string, field: string, value: string, reason = "Исправлено специалистом") =>
    api<void>("rules", `/packages/${packageId}/fields`, { method: "PUT", body: { documentId, field, value, reason } }),
  retype: (docId: string, type: string, reason = "Вид исправлен специалистом") => api<void>("docproc", `/documents/${docId}/type`, { method: "PUT", body: { type, reason } }),
  reprocess: (fileId: string) => api<void>("docproc", `/files/${fileId}/reprocess`, { method: "POST" }),
  rebuild: (packageId: string) => api<{ documents?: number; updated?: number; removed?: number }>("rules", `/packages/${packageId}/projection/rebuild`, { method: "POST" }),
  expiring: (days: number) => api<{ items?: Expiring[] }>("rules", `/expiring${qs({ days })}`),
  types,
  model: () => api<ModelStatus>("docproc", "/model/status"),
};

export const reviewKeys = {
  queue: (f: QueueFilter) => ["review", "queue", f] as const,
  queueAll: ["review", "queue"] as const,
  count: (status: string) => ["review", "queue", "count", status] as const,
  card: (id: string) => ["review", "card", id] as const,
  history: (id: string) => ["review", "history", id] as const,
  expiring: (days: number) => ["review", "expiring", days] as const,
  types: ["docproc", "types"] as const,
  model: ["docproc", "model"] as const,
};
