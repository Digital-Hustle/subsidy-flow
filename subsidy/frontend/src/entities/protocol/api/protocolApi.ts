import { api, download } from "@/shared/api";
import type { Expiring, RebuildResult, HistoryEntry, Program, Protocol, ProtocolSummary, Stats } from "../model/types";

const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

/** Протоколы, правки полей, история, статистика, программы → rules. */
export const protocolApi = {
  latest: (packageId: string) => api<Protocol>("rules", `/packages/${packageId}/protocol`),
  versions: (packageId: string) => api<{ items: ProtocolSummary[] }>("rules", `/packages/${packageId}/protocols`),
  version: (packageId: string, v: number) => api<Protocol>("rules", `/packages/${packageId}/protocols/${v}`),
  correct: (packageId: string, documentId: string, field: string, value: string, reason: string) =>
    api<void>("rules", `/packages/${packageId}/fields`, { method: "PUT", body: { documentId, field, value, reason } }),
  exportFile: (packageId: string, format: "docx" | "xlsx" | "pdf") =>
    download("rules", `/packages/${packageId}/protocol/export?format=${format}`, `protocol-${packageId.slice(0, 8)}.${format}`),
  history: (inn: string) => api<{ items: HistoryEntry[] }>("rules", `/organizations/${inn}/history`),
  stats: (f: { from?: string; to?: string; program?: string }) => api<Stats>("rules", `/stats/errors${qs(f)}`),
  expiring: (days: number) => api<{ items: Expiring[] }>("rules", `/expiring${qs({ days })}`),
  programs: () => api<{ items: Program[] }>("rules", "/programs"),
  rebuild: (packageId: string) =>
    api<RebuildResult>("rules", `/packages/${packageId}/projection/rebuild`, { method: "POST" }),
  program: (code: string) => api<Program>("rules", `/programs/${code}`),
  saveProgram: (code: string, body: { name: string; params: unknown; checklist: unknown }) =>
    api<Program>("rules", `/programs/${code}`, { method: "PUT", body }),
};

export const protocolKeys = {
  latest: (packageId: string) => ["rules", "protocol", packageId] as const,
  versions: (packageId: string) => ["rules", "protocols", packageId] as const,
  version: (packageId: string, v: number) => ["rules", "protocol", packageId, v] as const,
  history: (inn: string) => ["rules", "history", inn] as const,
  stats: (f: object) => ["rules", "stats", f] as const,
  expiring: (days: number) => ["rules", "expiring", days] as const,
  programs: ["rules", "programs"] as const,
  program: (code: string) => ["rules", "program", code] as const,
};
