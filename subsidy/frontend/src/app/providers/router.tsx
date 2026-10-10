import { useRef } from "react";
import { createBrowserRouter, Navigate, useLocation, useOutlet } from "react-router";
import { AnimatePresence, motion, useIsPresent } from "motion/react";
import { Aurora } from "@/shared/ui";
import { tween } from "@/shared/config/motion";
import { routes } from "@/shared/const/router";
import { HomePage } from "@/pages/home";
import { SpecialistShell } from "@/widgets/specialist-shell";

// Каждая страница — отдельный чанк (recharts, просмотрщик и т. п. не грузятся на главной).
// react-router дожидается чанка до смены экрана, поэтому переход без мигания пустого экрана.
const packages = () => import("@/pages/packages");
const pkg = () => import("@/pages/package");
const expiring = () => import("@/pages/expiring");
const stats = () => import("@/pages/stats");
const programs = () => import("@/pages/programs");
const org = () => import("@/pages/organization");
const staff = () => import("@/pages/staff");
const settings = () => import("@/pages/settings");
const login = () => import("@/pages/login");

/** Фоновая предзагрузка страниц после первого экрана: дальнейшие переходы мгновенные. */
export const prefetchRoutes = () => {
  const run = () => [packages, pkg, expiring, stats, programs, org, staff, settings].forEach((f) => { f().catch(() => {}); });
  if ("requestIdleCallback" in window) requestIdleCallback(run); else setTimeout(run, 1);
};

/** Уходящий раздел дорисовывает свой последний экран, пока гаснет (иначе он мгновенно подменился бы новым). */
const Frozen = () => {
  const outlet = useOutlet();
  const present = useIsPresent();
  const last = useRef(outlet);
  if (present) last.current = outlet;
  return last.current;
};

/**
 * Общий корень: одно живое сияние на все разделы (не пересоздаётся при переходе — фон не мигает)
 * и мягкая смена раздела: главная ↔ рабочее место — затухание и проявление.
 * Внутри раздела страницы меняются своим pageIn (оболочка и шапка остаются на месте).
 */
const RootLayout = () => {
  const { pathname } = useLocation();
  const section = pathname.split("/")[1] || "home";
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <Aurora fixed />
      <AnimatePresence mode="wait" initial={false} onExitComplete={() => scrollTo(0, 0)}>
        <motion.div key={section} initial={{ opacity: 0 }} animate={{ opacity: 1, transition: tween.base }} exit={{ opacity: 0, transition: tween.exit }}>
          <Frozen />
        </motion.div>
      </AnimatePresence>
    </div>
  );
};

// Зона специалиста /specialist (rules.md §4), главная — «/».
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { path: routes.home, element: <HomePage /> },
      { path: routes.login, lazy: async () => ({ Component: (await login()).LoginPage }) },
      {
        path: routes.specialist,
        element: <SpecialistShell />,
        children: [
          { index: true, element: <Navigate to={routes.packages} replace /> },
          { path: "packages", lazy: async () => ({ Component: (await packages()).PackagesPage }) },
          { path: "packages/:id", lazy: async () => ({ Component: (await pkg()).PackagePage }) },
          { path: "expiring", lazy: async () => ({ Component: (await expiring()).ExpiringPage }) },
          { path: "stats", lazy: async () => ({ Component: (await stats()).StatsPage }) },
          { path: "programs", lazy: async () => ({ Component: (await programs()).ProgramsPage }) },
          { path: "organizations/:inn", lazy: async () => ({ Component: (await org()).OrganizationPage }) },
          { path: "staff", lazy: async () => ({ Component: (await staff()).StaffPage }) },
          { path: "settings", lazy: async () => ({ Component: (await settings()).SettingsPage }) },
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to={routes.home} replace /> },
]);
