import { api } from "@/shared/api";
import { uid } from "@/shared/lib";
import type { ApplicantApplication, ApplicationData, Created, Program, RequiredDocument, Uploaded } from "../model/types";

const base = (id: string) => `/applicant/applications/${id}`;

/** Кабинет заявителя → package /applicant (docs/API.md). Ключ заявки подставляет shared/api (Authorization: Applicant <key>). */
export const applicantApi = {
  programs: () => api<{ items?: Program[] }>("package", "/applicant/programs").then((r) => r.items ?? []),
  create: (programCode: string, contact: { email: string; phone?: string }) =>
    api<Created>("package", "/applicant/applications", { method: "POST", body: { consentPd: true, programCode, contact }, headers: { "Idempotency-Key": uid() } }),
  resume: (phoneOrEmail: string, resumeCode: string) =>
    api<Created>("package", "/applicant/resume", { method: "POST", body: { phoneOrEmail, resumeCode } }),
  get: (id: string) => api<ApplicantApplication>("package", base(id)),
  saveApplication: (id: string, expectedRevision: number | undefined, application: ApplicationData) =>
    api<{ applicationRevision?: number; requiredDocuments?: RequiredDocument[] }>("package", `${base(id)}/application`, { method: "PUT", body: { expectedRevision: expectedRevision ?? 0, application } }),
  upload: (id: string, file: File, slotType?: string) => {
    const fd = new FormData();
    fd.append("file", file);
    const q = slotType ? `?slotType=${encodeURIComponent(slotType)}` : "";
    return api<Uploaded>("package", `${base(id)}/documents${q}`, { method: "POST", body: fd, headers: { "Idempotency-Key": uid() } });
  },
  addFile: (id: string, docId: string, file: File, mode: "add" | "replace") => {
    const fd = new FormData();
    fd.append("file", file);
    return api<Uploaded>("package", `${base(id)}/documents/${docId}/files?mode=${mode}`, { method: "POST", body: fd, headers: { "Idempotency-Key": uid() } });
  },
  remove: (id: string, docId: string) => api<void>("package", `${base(id)}/documents/${docId}`, { method: "DELETE" }),
  deleteApplication: (id: string) => api<void>("package", base(id), { method: "DELETE" }),
  submit: (id: string) => api<{ status?: string; missing?: string[] }>("package", `${base(id)}/submit`, { method: "POST" }),
};

export const applicantKeys = {
  programs: ["applicant", "programs"] as const,
  application: (id: string) => ["applicant", "application", id] as const,
};
