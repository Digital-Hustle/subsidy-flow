import { useEffect, useSyncExternalStore } from "react";
import { api, setAuth } from "@/shared/api";

export type Role = "SPECIALIST" | "SENIOR_SPECIALIST" | "AUDITOR";
export type Perm = "work" | "decide" | "programs" | "reprocess" | "stats" | "staff";
export type StaffStatus = "INVITED" | "ACTIVE" | "DISABLED";
export type StaffUser = { id: string; name: string; email: string; role: Role; status: StaffStatus; demo: boolean; lastSeen: number | null };
export type StandMode = "demo" | "prod";

type SessionRs = { accessToken: string; expiresAt: number; sessionExpiresAt: number; user: StaffUser };
export type CodeSent = { sentTo: string; expiresAt: number; resendAt: number; demoCode: string | null };
export type Mode = { mode: StandMode; demo: StaffUser[]; demoCode: string | null };

/** Права зеркалят матрицу ролей бэка (docs/API.md §Аутентификация). Решает сервер — здесь только что показывать. */
const PERMS: Record<Role, Perm[]> = {
  SPECIALIST: ["work", "stats"],
  SENIOR_SPECIALIST: ["work", "decide", "programs", "reprocess", "stats", "staff"],
  AUDITOR: ["stats"],
};
const LABEL: Record<Role, string> = { SPECIALIST: "специалист", SENIOR_SPECIALIST: "старший специалист", AUDITOR: "аудитор" };
export const ROLES: Role[] = ["SPECIALIST", "SENIOR_SPECIALIST", "AUDITOR"];

export const can = (roles: Role[], p: Perm) => roles.some((r) => PERMS[r].includes(p));
export const permsOf = (r: Role) => PERMS[r];
export const roleLabel = (r: Role | null) => (r ? LABEL[r] : "нет роли");
export const LOGIN_CODE_LEN = 6;

type State = { user: StaffUser | null; ready: boolean; mode: StandMode | null };
let state: State = { user: null, ready: false, mode: null };
const subs = new Set<() => void>();
const set = (s: Partial<State>) => { state = { ...state, ...s }; subs.forEach((f) => f()); };

/**
 * Сессия сотрудника (ADR-023): access-токен живёт только в памяти вкладки (RULES §Избегай — не в localStorage),
 * продление — по httpOnly-cookie `subsidy_session`, которую браузер шлёт только на /package/api/v1/auth.
 */
let token: { value: string; expiresAt: number } | null = null;

const auth = <T,>(path: string, body?: unknown) =>
  api<T>("package", `/auth${path}`, { method: body === undefined ? "GET" : "POST", body: body ?? undefined, session: true });

export const getMode = () => auth<Mode>("/mode");
export const requestCode = (email: string) => auth<CodeSent>("/code", { email: email.trim().toLowerCase() });

const started = (r: SessionRs) => {
  token = { value: r.accessToken, expiresAt: r.expiresAt };
  set({ user: r.user, ready: true });
  return r.user;
};
export const verifyCode = async (email: string, code: string) => started(await auth<SessionRs>("/verify", { email, code }));

/** Параллельные запросы ждут одного продления; не вышло — сессии нет, оболочка покажет вход. */
let renewing: Promise<boolean> | null = null;
const renew = () => {
  renewing ??= auth<SessionRs>("/refresh", {})
    .then((r) => { started(r); return true; })
    .catch(() => { token = null; set({ user: null, ready: true }); return false; })
    .finally(() => { renewing = null; });
  return renewing;
};

/** Токен, которому осталось жить меньше этого, обновляем до запроса (фоновые вкладки пропускают таймеры). */
const REFRESH_AHEAD_MS = 30_000;
const bearer = async () => {
  if (token && token.expiresAt - Date.now() < REFRESH_AHEAD_MS) await renew();
  return token ? `Bearer ${token.value}` : null;
};
const unauthorized = () => { void renew(); };

export const signOut = async () => {
  try { await auth<void>("/logout", {}); } catch { /* сессия и так закрыта */ }
  token = null;
  set({ user: null });
};

/**
 * Источник токена ставим синхронно при рендере оболочки — до того, как дочерние страницы начнут запросы
 * (эффекты детей выполняются раньше эффектов родителя, иначе первые запросы уходят без токена и получают 401).
 * При открытии вкладки сессия восстанавливается из cookie, режим стенда читается один раз.
 */
export const useSpecialistAuth = () => {
  setAuth(bearer, unauthorized);
  useEffect(() => {
    if (state.ready && state.mode) return;
    void getMode().then((m) => set({ mode: m.mode })).catch(() => {});
    if (!state.ready) void renew();
  }, []);
};

export const useSession = () => {
  const s = useSyncExternalStore((f) => { subs.add(f); return () => subs.delete(f); }, () => state);
  const roles: Role[] = s.user && s.user.status !== "DISABLED" ? [s.user.role] : [];
  return {
    ready: s.ready,
    user: s.user,
    roles,
    role: s.user?.role ?? null,
    mode: s.mode,
    demoStand: s.mode === "demo",
    name: s.user?.name ?? "Специалист",
    login: s.user?.email ?? "",
    signOut,
  };
};
