import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "./problem";

/** Серверные данные — только через TanStack Query (RULES §Делай): кэш, повтор, инвалидация после мутаций. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      // 4xx не повторяем: это ответ сервера, а не сбой связи
      retry: (n, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && n < 2,
    },
  },
});
