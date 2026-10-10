import { useState } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from "react-router";
import { motion } from "motion/react";
import { FolderOpen, CalendarClock, BarChart3, ListChecks, MoreHorizontal, Home, ChevronRight, UserCog, LogOut, ShieldCheck, FolderPlus, Users, Settings, FlaskConical } from "lucide-react";
import { useSession, useSpecialistAuth, can, roleLabel, type Perm } from "@/entities/session";
import { Avatar, Button, Dialog, EmptyState, HeaderBar, Logo, NavTrack, OfflineBanner, PreferencesButton, Spinner, Status, ThemePicker, TrackIndicator, useTrackIndicator } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";
import { duration, pageIn, press } from "@/shared/config/motion";
import { ModelStatusBadge } from "./ModelStatusBadge";

const NAV: { to: string; label: string; short: string; icon: typeof FolderOpen; perm: Perm }[] = [
  { to: routes.packages, label: "Пакеты", short: "Пакеты", icon: FolderOpen, perm: "stats" },
  { to: routes.expiring, label: "Истекающие", short: "Сроки", icon: CalendarClock, perm: "stats" },
  { to: routes.stats, label: "Частые ошибки", short: "Ошибки", icon: BarChart3, perm: "stats" },
  { to: routes.programs, label: "Программы", short: "Программы", icon: ListChecks, perm: "stats" },
];

/** Рабочее место: SSO (PKCE) с входом на странице, разделы по ролям, статус модели, тема; на телефоне — нижняя панель. */
export const SpecialistShell = () => {
  useSpecialistAuth();
  const s = useSession();
  const loc = useLocation();
  const nav = useNavigate();
  const [account, setAccount] = useState(false);
  const [more, setMore] = useState(false);
  const ind = useTrackIndicator(loc.pathname);
  if (!s.ready) return <div className="grid min-h-svh place-items-center"><Spinner className="size-8" /></div>;
  if (!s.user) return <Navigate to={`${routes.login}?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />;
  const items = NAV.filter((n) => can(s.roles, n.perm));
  return (
    <div className="relative isolate min-h-svh text-foreground">
      {/* Шапка-пилюля, как на sberbank.ru */}
      <HeaderBar>
        <Link to={routes.home} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="специалист" /></Link>
        <NavTrack items={items.map(({ to, label }) => ({ to, label }))} className="ml-auto hidden lg:block xl:ml-6" />
        <div className="ml-auto flex shrink-0 items-center gap-1.5">
          {s.demoStand && <Status tone="info" className="hidden lg:inline-flex"><FlaskConical />Демо-стенд</Status>}
          <ModelStatusBadge className="hidden xl:inline-flex" />
          <PreferencesButton className="hidden lg:flex" />
          <Button variant="quiet" className="hidden h-12 rounded-md md:inline-flex" onClick={() => setAccount(true)} aria-label={`Пользователь: ${s.name}, ${roleLabel(s.role)}`}>
            <Avatar name={s.name} className="size-7" /><span className="hidden 2xl:inline">{roleLabel(s.role)}</span>
          </Button>
          {can(s.roles, "work") && (<>
            <Link to={`${routes.packages}?new=1`} className="hidden md:block" tabIndex={-1}><Button variant="brand" className="h-12 rounded-md"><FolderPlus />Новый пакет</Button></Link>
            <Link to={`${routes.packages}?new=1`} className="md:hidden" tabIndex={-1}><Button variant="brand" size="icon" className="size-12" aria-label="Новый пакет"><FolderPlus /></Button></Link>
          </>)}
        </div>
      </HeaderBar>

      <OfflineBanner />
      <motion.main key={loc.pathname} {...pageIn} className="mx-auto w-full max-w-screen-2xl px-4 pb-nav pt-6 sm:px-6 sm:pt-8 lg:px-8 lg:pb-16">
        {s.roles.length ? <Outlet /> : <EmptyState icon={<ShieldCheck />} title="Нет роли" text="Учётной записи не назначена роль специалиста — обратитесь к администратору" action={<Button variant="secondary" onClick={() => setAccount(true)}><UserCog />Учётная запись</Button>} className="py-24" />}
      </motion.main>

      {/* Нижняя навигация телефона: плавающая панель */}
      <nav className="fixed inset-x-0 bottom-0 z-nav px-3 pb-safe lg:hidden" aria-label="Разделы">
        <div ref={ind.ref} className="relative mx-auto mb-3 flex max-w-md items-stretch gap-0.5 rounded-xl p-1 shadow-float glass-round [--clip-r:var(--radius-xl)]">
          <TrackIndicator ind={ind} className="inset-y-1 rounded-lg bg-accent" />
          {items.map(({ to, short, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => cn("relative flex min-h-control-lg min-w-0 flex-1 flex-col items-center justify-center gap-0.5 rounded-md text-xs font-medium outline-none", isActive ? "text-accent-foreground" : "text-muted-foreground")}>
              <Icon className="relative z-raised size-5" /><span className="relative z-raised max-w-full truncate tracking-tight">{short}</span>
            </NavLink>
          ))}
          <motion.button type="button" {...press} onClick={() => setMore(true)} className="relative flex min-h-control-lg min-w-0 flex-1 flex-col items-center justify-center gap-0.5 rounded-md text-xs font-medium text-muted-foreground">
            <MoreHorizontal className="relative z-raised size-5" /><span className="relative z-raised">Ещё</span>
          </motion.button>
        </div>
      </nav>

      <Dialog open={more} onClose={() => setMore(false)} title="Ещё">
        <div className="flex flex-col gap-1">
          <button type="button" onClick={() => { setMore(false); setAccount(true); }} className="flex min-h-control-lg items-center gap-3 rounded-md px-3 text-left text-base font-medium transition-colors duration-fast hover:bg-surface">
            <UserCog className="size-5 shrink-0" /><span className="flex-1">{s.name}<span className="block text-sm font-normal text-muted-foreground">{roleLabel(s.role)}</span></span><ChevronRight className="size-4 text-subtle-foreground" />
          </button>
          {can(s.roles, "staff") && (
            <button type="button" onClick={() => { setMore(false); nav(routes.staff); }} className="flex min-h-control-lg items-center gap-3 rounded-md px-3 text-left text-base font-medium transition-colors duration-fast hover:bg-surface">
              <Users className="size-5 shrink-0" /><span className="flex-1">Сотрудники</span><ChevronRight className="size-4 text-subtle-foreground" />
            </button>
          )}
          <button type="button" onClick={() => { setMore(false); nav(routes.settings); }} className="flex min-h-control-lg items-center gap-3 rounded-md px-3 text-left text-base font-medium transition-colors duration-fast hover:bg-surface">
            <Settings className="size-5 shrink-0" /><span className="flex-1">Настройки</span><ChevronRight className="size-4 text-subtle-foreground" />
          </button>
          <button type="button" onClick={() => { setMore(false); nav(routes.home); }} className="flex min-h-control-lg items-center gap-3 rounded-md px-3 text-left text-base font-medium transition-colors duration-fast hover:bg-surface">
            <Home className="size-5 shrink-0" /><span className="flex-1">На главную</span><ChevronRight className="size-4 text-subtle-foreground" />
          </button>
          <div className="mt-3 flex flex-col gap-3 border-t border-border px-1 pt-4">
            <span className="px-2 text-sm font-medium text-muted-foreground">Модель</span><ModelStatusBadge className="self-start" />
            <span className="px-2 text-sm font-medium text-muted-foreground">Оформление</span><ThemePicker onPick={() => setTimeout(() => setMore(false), duration.slow * 1000)} />
          </div>
        </div>
      </Dialog>

      <Dialog open={account} onClose={() => setAccount(false)} title="Учётная запись" description="Вход — по одноразовому коду на рабочую почту. Роль назначает старший специалист, права проверяет сервер">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3 rounded-lg bg-muted p-4">
            <Avatar name={s.name} className="size-11" />
            <span className="flex min-w-0 flex-1 flex-col gap-0.5"><span className="truncate font-medium">{s.name}</span><span className="truncate text-sm text-muted-foreground">{s.login}</span></span>
            <Status tone="success">{roleLabel(s.role)}</Status>
          </div>
          <ModelStatusBadge className="self-start" />
          {s.demoStand && <p className="text-pretty text-sm text-muted-foreground">Демо-стенд: код входа показывается на странице входа, а код 000000 пускает любого заведённого сотрудника.</p>}
          <div className="grid gap-2 sm:grid-cols-2">
            {can(s.roles, "staff") && <Button variant="secondary" onClick={() => { setAccount(false); nav(routes.staff); }}><Users />Сотрудники</Button>}
            <Button variant="secondary" onClick={() => { setAccount(false); nav(routes.settings); }}><Settings />Настройки</Button>
            <Button variant="danger-soft" className="sm:col-span-2" onClick={() => void s.signOut()}><LogOut />Выйти</Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
};
