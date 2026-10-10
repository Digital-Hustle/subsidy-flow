import { useRef } from "react";
import { Navigate, createBrowserRouter, useLocation, useOutlet, useParams } from "react-router";
import { AnimatePresence, motion, useIsPresent } from "motion/react";
import { Aurora } from "@/shared/ui";
import { tween } from "@/shared/config/motion";
import { HomePage } from "@/pages/home";
import { NotFoundPage } from "@/pages/not-found";
import { ApplyShell } from "@/widgets/apply-shell";
import { ErrorScreen } from "./ErrorScreen";

// Зоны — отдельные чанки: кабинет заявителя на телефоне не тянет код специалиста.
const apply = () => import("@/pages/apply");
const operator = () => import("@/pages/operator");
const operatorShell = () => import("@/widgets/operator-shell");

/** Подгрузка чанков в простое: заявителю — только кабинет, сотруднику — его зону (переходы без спиннера). */
export const prefetchRoutes = (pathname: string) => {
  const list = pathname.startsWith("/operator") ? [operator, operatorShell] : [apply];
  const run = () => list.forEach((f) => { f().catch(() => {}); });
  if ("requestIdleCallback" in window) requestIdleCallback(run); else setTimeout(run, 1);
};

const Frozen = () => {
  const outlet = useOutlet();
  const present = useIsPresent();
  const last = useRef(outlet);
  if (present) last.current = outlet;
  return last.current;
};

/** Общий корень как в «Проходной»: одно сияние на все зоны и мягкая смена зоны. */
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

/** Старые адреса зоны специалиста (/specialist/...) — из писем и закладок — ведут в новую зону /operator. */
const LegacyPackage = () => <Navigate to={`/operator/applications/${useParams().id ?? ""}`} replace />;
const SAME = new Set(["expiring", "programs", "staff"]);
const LegacySection = () => { const s = useParams().section ?? ""; return <Navigate to={SAME.has(s) ? `/operator/${s}` : "/operator"} replace />; };
const LegacyOrg = () => <Navigate to={`/operator/organizations/${useParams().inn ?? ""}`} replace />;

// BrowserRouter: Caddy отдаёт index.html на любые пути (try_files), ключ заявки — во фрагменте #k=.
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <ErrorScreen />,
    children: [
      { path: "/", element: <HomePage /> },
      {
        path: "/apply", element: <ApplyShell />,
        children: [
          { index: true, lazy: async () => ({ Component: (await apply()).ApplyStartPage }) },
          { path: ":id", lazy: async () => ({ Component: (await apply()).ApplyApplicationPage }) },
        ],
      },
      {
        path: "/operator", lazy: async () => ({ Component: (await operatorShell()).OperatorShell }),
        children: [
          { index: true, lazy: async () => ({ Component: (await operator()).QueuePage }) },
          { path: "applications/:id", lazy: async () => ({ Component: (await operator()).ApplicationPage }) },
          { path: "expiring", lazy: async () => ({ Component: (await operator()).ExpiringPage }) },
          { path: "metrics", lazy: async () => ({ Component: (await operator()).MetricsPage }) },
          { path: "organizations/:inn", lazy: async () => ({ Component: (await operator()).OrganizationPage }) },
          { path: "programs", lazy: async () => ({ Component: (await operator()).ProgramsPage }) },
          { path: "staff", lazy: async () => ({ Component: (await operator()).StaffPage }) },
        ],
      },
      { path: "/specialist/packages/:id", element: <LegacyPackage /> },
      { path: "/specialist/organizations/:inn", element: <LegacyOrg /> },
      { path: "/specialist/stats", element: <Navigate to="/operator/metrics" replace /> },
      { path: "/specialist/:section", element: <LegacySection /> },
      { path: "/specialist", element: <Navigate to="/operator" replace /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
