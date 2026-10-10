import { useEffect, useSyncExternalStore } from "react";
import { api, setAuth } from "@/shared/api";

export type Role = "SPECIALIST" | "SENIOR_SPECIALIST" | "AUDITOR";
export type Perm = "queue" | "act" | "decide" | "reprocess" | "export" | "reports" | "metrics" | "history" | "staff" | "programs" | "assistant";
export type StaffStatus = "INVITED" | "ACTIVE" | "DISABLED";
export type StaffUser = { id: string; name: string; email: string; role: Role; status: StaffStatus; demo: boolean; lastSeen: number | string | null };

/** Права зеркалят матрицу ролей бэка (docs/API.md §Аутентификация). Решает всё равно сервер — здесь только что показывать. */
const PERMS: Record<Role, Perm[]> = {
  SPECIALIST: ["queue", "act", "export", "reports", "metrics", "history", "assistant"],
  SENIOR_SPECIALIST: ["queue", "act", "decide", "reprocess", "export", "reports", "metrics", "history", "staff", "programs", "assistant"],
  AUDITOR: ["queue", "export", "reports", "metrics", "history", "assistant"],
};
const LABEL: Record<Role, string> = { SPECIALIST: "специалист", SENIOR_SPECIALIST: "старший специалист", AUDITOR: "аудитор" };
export const ROLES: Role[] = ["SPECIALIST", "SENIOR_SPECIALIST", "AUDITOR"];

type SessionRs = { accessToken: string; expiresAt: number; sessionExpiresAt: number; user: StaffUser };
/** Код отправлен: куда (маска), когда истечёт и когда можно просить новый. На демо-стенде код приходит в ответе. */
export type CodeSent = { sentTo: string; expiresAt: number; resendAt: number; demoCode: string | null };
export type AuthMode = { mode: "demo" | "prod"; demo: StaffUser[]; demoCode: string | null };

type State = { user: StaffUser | null; ready: boolean; mode: AuthMode["mode"] | null };
let state: State = { user: null, ready: false, mode: null };
const subs = new Set<() => void>();
const set = (s: Partial<State>) => { state = { ...state, ...s }; subs.forEach((f) => f()); };

/**
 * Сессия сотрудника (ADR-023): access-токен живёт только в памяти вкладки, продление — по httpOnly-cookie
 * `subsidy_session`, которую браузер шлёт только на /package/api/v1/auth.
 */
let token: { value: string; expiresAt: number } | null = null;

const auth = <T,>(path: string, body?: unknown) =>
  api<T>("package", `/auth${path}`, { method: body === undefined ? "GET" : "POST", body, session: true });

const norm = (email: string) => email.trim().toLowerCase();
const ms = (t: number | string) => (typeof t === "number" ? (t < 1e12 ? t * 1000 : t) : Date.parse(t));

/** Режим стенда: в demo — список демо-сотрудников для быстрого входа. */
export const authMode = () => auth<AuthMode>("/mode").then((m) => { set({ mode: m.mode }); return m; });
/** package выдаёт одноразовый код и шлёт письмо; на демо-стенде код приходит и в ответе. */
export const requestLoginCode = (email: string) =>
  auth<CodeSent>("/code", { email: norm(email) }).then((r) => ({ ...r, expiresAt: ms(r.expiresAt), resendAt: ms(r.resendAt) }));

const started = (r: SessionRs) => {
  token = { value: r.accessToken, expiresAt: ms(r.expiresAt) };
  set({ user: r.user, ready: true });
  return r.user;
};
/** Вход без пароля: почта + 6 цифр из письма. */
export const signInWithCode = async (email: string, code: string) => started(await auth<SessionRs>("/verify", { email: norm(email), code }));

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

const signOut = async () => {
  try { await auth<void>("/logout", {}); } catch { /* сессия и так закрыта */ }
  token = null;
  set({ user: null });
};

export const can = (roles: Role[], p: Perm) => roles.some((r) => PERMS[r].includes(p));
export const roleLabel = (r: Role | null) => (r ? LABEL[r] : "нет роли");

/**
 * Зона специалиста: каждый запрос — с Bearer-токеном. Источник токена ставим синхронно при рендере оболочки —
 * до того, как дочерние страницы начнут запросы (эффекты детей выполняются раньше эффектов родителя).
 */
export const useOperatorAuth = () => {
  setAuth(bearer, unauthorized);
  useEffect(() => {
    if (!state.mode) void authMode().catch(() => {});
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
    demoStand: s.mode === "demo",
    id: s.user?.id ?? "",
    name: s.user?.name || "Специалист",
    login: s.user?.email ?? "",
    signOut,
  };
};
