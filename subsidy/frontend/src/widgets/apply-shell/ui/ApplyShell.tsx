import { Link, Outlet, useLocation } from "react-router";
import { motion } from "motion/react";
import { HeaderBar, Logo, OfflineBanner, PreferencesButton } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { pageIn } from "@/shared/config/motion";

/** Кабинет заявителя: одна колонка, работает и с телефона, крупные цели касания. */
export const ApplyShell = () => {
  const loc = useLocation();
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <HeaderBar inner="max-w-2xl">
        <Link to={routes.home} aria-label="На главную" className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="кабинет заявителя" /></Link>
        <div className="ml-auto flex items-center gap-1.5"><PreferencesButton /></div>
      </HeaderBar>
      <OfflineBanner text="Нет связи — загрузка продолжится, когда связь вернётся" />
      <motion.main key={loc.pathname} {...pageIn} className="mx-auto w-full max-w-2xl px-4 pb-16 pt-6 sm:px-6 sm:pt-8">
        <Outlet />
      </motion.main>
    </div>
  );
};
