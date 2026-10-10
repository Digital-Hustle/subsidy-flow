import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { MotionConfig } from "motion/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster, applyStoredTheme } from "@/shared/ui";
import { router, prefetchRoutes } from "./providers/router";
import { queryClient } from "./providers/query";
import "./styles/index.css";

// Тема — до первой отрисовки (inline-скрипт в index.html запрещён CSP script-src 'self').
applyStoredTheme();
prefetchRoutes(location.pathname);

// Заставка из index.html: плавно гасим после первой отрисовки приложения (FE-UI-01)
requestAnimationFrame(() => requestAnimationFrame(() => {
  const s = document.getElementById("splash");
  if (!s) return;
  s.classList.add("out");
  s.addEventListener("transitionend", () => s.remove(), { once: true });
}));

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        <RouterProvider router={router} />
        <Toaster />
      </MotionConfig>
    </QueryClientProvider>
  </StrictMode>,
);
