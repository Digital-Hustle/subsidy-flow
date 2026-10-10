import { api } from "@/shared/api";
import type { Role, StaffUser } from "../model/session";

/** Сотрудники (package, только старший специалист): список, приглашение, роль и доступ. */
export const staffApi = {
  list: () => api<{ items: StaffUser[] }>("package", "/staff"),
  invite: (body: { name: string; email: string; role: Role }) => api<StaffUser>("package", "/staff", { method: "POST", body }),
  update: (id: string, body: { role?: Role; active?: boolean }) => api<StaffUser>("package", `/staff/${id}`, { method: "PATCH", body }),
};

export const staffKeys = { list: ["package", "staff"] as const };
