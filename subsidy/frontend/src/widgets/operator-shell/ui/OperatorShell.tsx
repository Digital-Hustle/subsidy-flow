import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router";
import { motion } from "motion/react";
import { FlaskConical, LogOut, ShieldCheck } from "lucide-react";
import { SignIn } from "./SignIn";
import { useSession, useOperatorAuth, can, roleLabel, type Perm } from "@/entities/session";
import { Avatar, Button, Dialog, EmptyState, HeaderBar, Logo, NavTrack, OfflineBanner, PreferencesButton, Spinner, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { pageIn } from "@/shared/config/motion";
import { ModelStatusBadge } from "./ModelStatusBadge";

const NAV: { to: string; label: string; perm: Perm; end?: boolean }[] = [
  { to: routes.operator, label: "Очередь", perm: "queue", end: true },
  { to: routes.operatorExpiring, label: "Сроки", perm: "reports" },
  { to: routes.operatorMetrics, label: "Ошибки", perm: "metrics" },
  { to: routes.operatorPrograms, label: "Программы", perm: "queue" },
  { to: routes.operatorStaff, label: "Сотрудники", perm: "staff" },
];

/** Зона специалиста: вход по коду из письма, разделы по ролям, статус модели, тема. */
export const OperatorShell = () => {
  useOperatorAuth();
  const s = useSession();
  const loc = useLocation();
  const [account, setAccount] = useState(false);
  if (!s.ready) return <div className="grid min-h-svh place-items-center"><Spinner className="size-8" /></div>;
  if (!s.user) return <SignIn />;
  const items = NAV.filter((n) => can(s.roles, n.perm));
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <HeaderBar>
        <Link to={routes.home} aria-label="На главную" className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="специалист" /></Link>
        <NavTrack items={items.map(({ to, label, end }) => ({ to, label, end }))} className="ml-auto hidden md:block xl:ml-6" />
        <div className="ml-auto flex shrink-0 items-center gap-1.5">
          {s.demoStand && <Status tone="warning" className="hidden sm:inline-flex"><FlaskConical className="size-3.5" />демо-стенд</Status>}
          <ModelStatusBadge className="hidden xl:inline-flex" />
          <PreferencesButton className="hidden sm:flex" />
          <Button variant="quiet" className="h-12 rounded-md" onClick={() => setAccount(true)} aria-label={`Пользователь: ${s.name}`}><Avatar name={s.name} className="size-7" /><span className="hidden xl:inline">{roleLabel(s.role)}</span></Button>
        </div>
      </HeaderBar>
      <OfflineBanner />
      <nav className="mx-auto mt-3 flex max-w-7xl px-4 md:hidden" aria-label="Разделы"><NavTrack items={items.map(({ to, label, end }) => ({ to, label, end }))} /></nav>
      <motion.main key={loc.pathname} {...pageIn} className="mx-auto w-full max-w-7xl px-4 pb-16 pt-6 sm:px-6 sm:pt-8">
        {s.roles.length ? <Outlet /> : <EmptyState icon={<ShieldCheck />} title="Нет роли" text="Учётная запись отключена или без роли — обратитесь к старшему специалисту" className="py-24" />}
      </motion.main>
      <Dialog open={account} onClose={() => setAccount(false)} title="Учётная запись">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3 rounded-lg bg-muted p-4">
            <Avatar name={s.name} className="size-11" />
            <span className="flex min-w-0 flex-1 flex-col"><span className="truncate font-medium">{s.name}</span><span className="truncate text-sm text-muted-foreground">{s.login}</span></span>
            <Status tone="success">{roleLabel(s.role)}</Status>
          </div>
          <ModelStatusBadge className="self-start" />
          <Button variant="danger-soft" onClick={() => void s.signOut()}><LogOut />Выйти</Button>
        </div>
      </Dialog>
    </div>
  );
};
