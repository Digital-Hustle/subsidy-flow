# Шаблоны (копируй и переименовывай)

## Entity `src/entities/Worker/`
```
Worker/
  api/dto.ts  api/workerApi.ts  api/queries.ts
  lib/mappers.ts
  model/types/worker.ts  model/consts/workerStatus.ts
  ui/WorkerCard/WorkerCard.tsx  ui/WorkerCard/WorkerCardSkeleton.tsx
  index.ts
```
```ts
// api/dto.ts — контракт бэка как есть (можно алиасить сгенерированные OpenAPI-типы)
import type { components } from "@/shared/api/schema";
export type WorkerDto = components["schemas"]["WorkerRs"];
export type WorkerListDto = components["schemas"]["PageRsWorkerRs"];

// model/consts/workerStatus.ts
export const WorkerStatus = { ACTIVE: "ACTIVE", BLOCKED: "BLOCKED" } as const;
export type WorkerStatus = (typeof WorkerStatus)[keyof typeof WorkerStatus];

// model/types/worker.ts
import type { WorkerStatus } from "../consts/workerStatus";
export interface Worker { id: string; fullName: string; status: WorkerStatus; hiredAt: Date }

// lib/mappers.ts
import type { WorkerDto } from "../api/dto";
import type { Worker } from "../model/types/worker";
export const mapWorkerDto = (dto: WorkerDto): Worker => ({
  id: dto.id,
  fullName: [dto.lastName, dto.firstName].filter(Boolean).join(" "),
  status: dto.status,
  hiredAt: new Date(dto.hiredAt),
});

// api/workerApi.ts
import { $api } from "@/shared/api/api";
import type { PageParams } from "@/shared/types/pagination";
import type { WorkerDto, WorkerListDto } from "./dto";
export const workerApi = {
  list: (params: PageParams) =>
    $api.get<WorkerListDto>("/people/api/v1/workers", { params }).then((r) => r.data),
  get: (id: string) => $api.get<WorkerDto>(`/people/api/v1/workers/${id}`).then((r) => r.data),
};

// api/queries.ts
import { useQuery } from "@tanstack/react-query";
import type { PageParams } from "@/shared/types/pagination";
import { mapWorkerDto } from "../lib/mappers";
import { workerApi } from "./workerApi";
export const workerKeys = {
  all: ["workers"] as const,
  lists: () => [...workerKeys.all, "list"] as const,
  list: (p: PageParams) => [...workerKeys.lists(), p] as const,
  detail: (id: string) => [...workerKeys.all, "detail", id] as const,
};
export const useWorkersQuery = (p: PageParams) =>
  useQuery({
    queryKey: workerKeys.list(p),
    queryFn: async () => {
      const page = await workerApi.list(p);
      return { ...page, content: page.content.map(mapWorkerDto) };
    },
  });

// index.ts — публичный API: только то, что нужно снаружи
export type { Worker } from "./model/types/worker";
export { WorkerStatus } from "./model/consts/workerStatus";
export { workerKeys, useWorkersQuery } from "./api/queries";
export { WorkerCard } from "./ui/WorkerCard/WorkerCard";
```

## Feature `src/features/block-worker/`
```ts
// api/mutations.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { workerKeys } from "@/entities/Worker";
import { $api } from "@/shared/api/api";
import { getProblemMessage } from "@/shared/api/problem";
export const useBlockWorkerMutation = () => {
  const qc = useQueryClient();
  const { t } = useTranslation("workers");
  return useMutation({
    mutationFn: (p: { id: string; reason: string }) =>
      $api.post(`/people/api/v1/workers/${p.id}/block`, { reason: p.reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: workerKeys.all });
      toast.success(t("block.success"));
    },
    onError: (e) => toast.error(getProblemMessage(e) ?? t("block.error")),
  });
};
// ui/BlockWorkerButton.tsx — memo-компонент с диалогом подтверждения, вызывает хук
// index.ts
export { BlockWorkerButton } from "./ui/BlockWorkerButton";
```
Оптимистичное обновление (только обратимые действия):
```ts
onMutate: async (vars) => {
  await qc.cancelQueries({ queryKey: workerKeys.detail(vars.id) });
  const prev = qc.getQueryData(workerKeys.detail(vars.id));
  qc.setQueryData(workerKeys.detail(vars.id), (old) => (old ? { ...old, ...vars } : old));
  return { prev };
},
onError: (_e, vars, ctx) => qc.setQueryData(workerKeys.detail(vars.id), ctx?.prev),
onSettled: (_d, _e, vars) => qc.invalidateQueries({ queryKey: workerKeys.detail(vars.id) }),
```

## Widget `src/widgets/WorkersTable/`
```tsx
// ui/WorkersTable.tsx — композиция entity + features; данные грузит сам
import { memo } from "react";
import { useTranslation } from "react-i18next";
import { useWorkersQuery, WorkerCard } from "@/entities/Worker";
import { BlockWorkerButton } from "@/features/block-worker";
import { ErrorState } from "@/shared/ui/ErrorState/ErrorState";
import { EmptyState } from "@/shared/ui/EmptyState/EmptyState";
export const WorkersTable = memo(() => {
  const { t } = useTranslation("workers");
  const q = useWorkersQuery({ page: 0, size: 20 });
  if (q.isPending) return <WorkersTableSkeleton />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  if (!q.data.content.length) return <EmptyState text={t("empty")} />;
  return q.data.content.map((w) => (
    <WorkerCard key={w.id} worker={w} actions={<BlockWorkerButton id={w.id} />} />
  ));
});
```

## Page `src/pages/WorkersPage/`
```tsx
// ui/WorkersPage.tsx
const WorkersPage = () => <PageLayout title="workers:title"><WorkersTable /></PageLayout>;
export default WorkersPage;
// ui/WorkersPage.async.tsx
import { lazy } from "react";
export const WorkersPageAsync = lazy(() => import("./WorkersPage"));
// index.ts
export { WorkersPageAsync as WorkersPage } from "./ui/WorkersPage.async";
```

## Маршрут
```ts
// shared/const/router.ts
export const AppRoutes = { ADMIN_WORKERS: "admin_workers", ... } as const;
export type AppRoutes = (typeof AppRoutes)[keyof typeof AppRoutes];
export const getRouteAdminWorkers = () => "/admin/workers";
export const getRouteAdminWorker = (id: string) => `/admin/workers/${id}`;

// app/providers/router/config/routeConfig.tsx
[AppRoutes.ADMIN_WORKERS]: {
  path: getRouteAdminWorkers(),
  element: <WorkersPage />,
  authOnly: true,
  roles: ["ADMIN", "MANAGER"],
},
```
Плюс `public/locales/ru/workers.json` с ключами `title`, `empty`, `block.success`, `block.error`.

## shared/api/api.ts (схема)
`$api = axios.create({ baseURL: env.API_URL })`; request-интерсептор кладёт `Authorization` из `tokenStorage.get()` (in-memory); response-интерсептор на 401 — один общий `refreshPromise` (не параллельные refresh), повтор запроса, при неудаче `onSessionExpired()`; на 403 — `onForbidden()`. Колбэки регистрирует `app` (зависимость направлена вниз).
