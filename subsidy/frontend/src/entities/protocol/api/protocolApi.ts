import { api } from "@/shared/api";
import type { OrgHistoryEntry, Program, Protocol, ProtocolVersion, Stats } from "../model/types";

const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

/** Протоколы, история организации, статистика ошибок, программы → rules. */
export const protocolApi = {
  latest: (packageId: string) => api<Protocol>("rules", `/packages/${packageId}/protocol`),
  versions: (packageId: string) => api<{ items?: ProtocolVersion[] } | ProtocolVersion[]>("rules", `/packages/${packageId}/protocols`).then((r) => (Array.isArray(r) ? r : r.items ?? [])),
  version: (packageId: string, v: number) => api<Protocol>("rules", `/packages/${packageId}/protocols/${v}`),
  orgHistory: (inn: string) => api<{ items?: OrgHistoryEntry[] } | OrgHistoryEntry[]>("rules", `/organizations/${inn}/history`).then((r) => (Array.isArray(r) ? r : r.items ?? [])),
  stats: (f: { from?: string; to?: string; program?: string }) => api<Stats>("rules", `/stats/errors${qs(f)}`),
  programs: () => api<{ items?: Program[] }>("rules", "/programs").then((r) => r.items ?? []),
  program: (code: string) => api<Program>("rules", `/programs/${code}`),
  programVersions: (code: string) => api<{ items?: Program[] }>("rules", `/programs/${code}/versions`).then((r) => r.items ?? []),
  createProgram: (body: { code: string; name: string; params: unknown; checklist: unknown }) => api<Program>("rules", "/programs", { method: "POST", body }),
  saveProgram: (code: string, body: { name?: string; params: unknown; checklist: unknown }) => api<Program>("rules", `/programs/${code}`, { method: "PUT", body }),
};

export const protocolKeys = {
  latest: (packageId: string) => ["rules", "protocol", packageId] as const,
  versions: (packageId: string) => ["rules", "protocols", packageId] as const,
  version: (packageId: string, v: number) => ["rules", "protocol", packageId, v] as const,
  orgHistory: (inn: string) => ["rules", "history", inn] as const,
  stats: (f: object) => ["rules", "stats", f] as const,
  programs: ["rules", "programs"] as const,
  program: (code: string) => ["rules", "program", code] as const,
  programVersions: (code: string) => ["rules", "program", code, "versions"] as const,
};
