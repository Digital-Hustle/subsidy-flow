import { api, apiBlob } from "@/shared/api";
import type { Created, DecisionResult, DocType, DocumentResult, ModelStatus, PackageCard, QueueFilter, QueuePage, UploadResult } from "../model/types";

const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

/** Пакеты и файлы → package; документы, виды и статус модели → docproc. */
export const packageApi = {
  queue: (f: QueueFilter, cursor: string | null, limit = 30) => api<QueuePage>("package", `/packages${qs({ ...f, cursor, limit })}`),
  card: (packageId: string) => api<PackageCard>("package", `/packages/${packageId}`),
  create: (orgInn: string, programCode: string) =>
    api<Created>("package", "/packages", { method: "POST", body: { orgInn, programCode }, headers: { "Idempotency-Key": crypto.randomUUID() } }),
  upload: (packageId: string, files: File[]) => {
    const form = new FormData();
    files.forEach((f) => form.append("files", f, f.name));
    return api<UploadResult>("package", `/packages/${packageId}/files`, { method: "POST", body: form });
  },
  replace: (packageId: string, fileId: string, file: File) => {
    const form = new FormData();
    form.append("file", file, file.name);
    return api<UploadResult>("package", `/packages/${packageId}/files/${fileId}/versions`, { method: "POST", body: form });
  },
  remove: (packageId: string, fileId: string) => api<void>("package", `/packages/${packageId}/files/${fileId}`, { method: "DELETE" }),
  decide: (packageId: string, decision: string, comment: string) =>
    api<DecisionResult>("package", `/packages/${packageId}/decision`, { method: "POST", body: { decision, comment: comment || null } }),
  page: (fileId: string, n: number, signal?: AbortSignal) => apiBlob("package", `/files/${fileId}/pages/${n}.png`, signal),
  documents: (packageId: string) => api<{ items: DocumentResult[] }>("docproc", `/packages/${packageId}/documents`),
  retype: (documentId: string, type: string, reason: string) =>
    api<void>("docproc", `/documents/${documentId}/type`, { method: "PUT", body: { type, reason } }),
  reprocess: (fileId: string) => api<void>("docproc", `/files/${fileId}/reprocess`, { method: "POST" }),
  types: () => api<{ items: DocType[] }>("docproc", "/doc-types"),
  model: () => api<ModelStatus>("docproc", "/model/status"),
};

export const packageKeys = {
  queueAll: ["package", "queue"] as const,
  queue: (f: QueueFilter) => ["package", "queue", f] as const,
  card: (packageId: string) => ["package", "card", packageId] as const,
  documents: (packageId: string) => ["docproc", "documents", packageId] as const,
  types: ["docproc", "types"] as const,
  model: ["docproc", "model"] as const,
};
