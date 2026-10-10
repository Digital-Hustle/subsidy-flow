import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api";

const STALE_MS = 10_000;

/** Кэш сервера: 4xx не повторяем (это ответ, а не сбой), сеть/5xx — до двух повторов. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: STALE_MS,
      refetchOnWindowFocus: true,
      retry: (n, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && n < 2,
    },
    mutations: { retry: false },
  },
});
