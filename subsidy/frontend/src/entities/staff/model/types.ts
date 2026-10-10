export type StaffRole = "SPECIALIST" | "SENIOR_SPECIALIST" | "AUDITOR";
export type StaffMember = { id: string; name: string; email: string; role: StaffRole; status: "INVITED" | "ACTIVE" | "DISABLED"; demo: boolean; lastSeen: number | string | null };

export const STAFF_ROLES: StaffRole[] = ["SPECIALIST", "SENIOR_SPECIALIST", "AUDITOR"];
export const STAFF_ROLE_TITLE: Record<StaffRole, string> = { SPECIALIST: "Специалист", SENIOR_SPECIALIST: "Старший специалист", AUDITOR: "Аудитор" };
