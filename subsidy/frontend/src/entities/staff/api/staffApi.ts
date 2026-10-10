import { api } from "@/shared/api";
import type { StaffMember, StaffRole } from "../model/types";

/** Сотрудники (package, только старший специалист): список, приглашение по почте, роль и доступ. */
export const staffApi = {
  list: () => api<{ items?: StaffMember[] }>("package", "/staff").then((r) => r.items ?? []),
  invite: (b: { name: string; email: string; role: StaffRole }) => api<StaffMember>("package", "/staff", { method: "POST", body: b }),
  update: (id: string, b: { role?: StaffRole; active?: boolean }) => api<StaffMember>("package", `/staff/${id}`, { method: "PATCH", body: b }),
};
export const staffKeys = { all: ["package", "staff"] as const };
