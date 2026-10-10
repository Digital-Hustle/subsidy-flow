# Адаптация под «Проходную» (subsidy/frontend)

Одно Vite-приложение по FSD (ADR-020) вместо трёх приложений в pnpm workspaces. Три группы маршрутов собираются в один бандл, но грузятся лениво — каждая зона тянет только свои страницы.

| Зона | Маршруты | Вход | Роли |
|---|---|---|---|
| Киоск | `/kiosk/*` | Keycloak (GUARD) + ключ киоска | `GUARD` |
| Рабочий (PWA) | `/worker/*` | инвайт → ключ устройства ECDSA, без пароля | device-токен |
| Админка | `/admin/*` | Keycloak SSO + TOTP | `ADMIN`, `MANAGER`, `SECURITY_OFFICER` |

## Отличия от эталона
1. **Токены не в localStorage.** `shared/api/tokenStorage.ts` — in-memory; OIDC через `oidc-client-ts` (`userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() })`, silent renew) для admin/kiosk. Рабочий: приватный ключ `crypto.subtle` (неизвлекаемый) в IndexedDB, короткий device-токен в памяти.
2. **Стек**: npm (не pnpm), Tailwind 4 + shadcn вместо Mantine, axios вместо openapi-fetch, zustand, react-hook-form + zod, i18next, react-hot-toast. Графики — `recharts` (MIT). Анимации — `motion` (MIT), только `motion/react` (ADR-031). QR — `qrcode.react`, сканер — `@zxing/browser`. PWA — `vite-plugin-pwa` с `scope: "/worker/"`.
3. **Типы API**: `npm run gen:api` (`openapi-typescript ../contracts/openapi/*.json -o src/shared/api/schema.ts`), `dto.ts` сущностей алиасит их. Файл генерится в CI, не коммитится.
4. **Моки только в dev**: MSW подключается в `main.tsx` под `import.meta.env.DEV && env.USE_MOCKS`. В production-сборке моков нет (проверяет `guard.sh`). Заглушка в боевом пути = снятие (rules.md §1).
5. **Решение о проходе не считается на клиенте.** Киоск показывает `decision / code / message / hint` из ответа access. Иконка и цвет по `code` — `entities/PassDecision/model/consts/reasonView.ts`.
6. **Роли** — `RequireRole roles={[...]}` в `AppRouter` на группу маршрутов; роли из `session`-стора (claims Keycloak `realm_access.roles` или device-токен). Меню админки строится из той же конфигурации, без дублирования списка ролей.
7. **Realtime**: `shared/api/sse.ts` — `EventSource` c `?ticket=` (короткий билет от `/shift/api/v1/stream/ticket`), переподключение с backoff; хук `useEventStream(topic, onEvent)` → `queryClient.invalidateQueries`.
8. **Нет связи**: `shared/hooks/useOnline` + баннер в `app`-layout каждой зоны.

## Карта слайсов (стартовая)
- entities: `Session`, `Worker`, `Site` (объект/зона), `Shift`, `PassAttempt`, `PassDecision`, `Device`, `Briefing`.
- features: `activate-device`, `show-pass-qr`, `scan-pass`, `capture-face`, `confirm-manual-pass`, `create-worker`, `block-worker`, `revoke-device`, `upload-face`, `export-timesheet`, `ask-assistant`.
- widgets: `KioskScanner`, `DecisionScreen`, `WorkerPassCard`, `OnSiteNow`, `PassJournalTable`, `ShiftTimeline`, `AssistantChat`, `AdminSidebar`.
- pages: `KioskPage`, `WorkerActivatePage`, `WorkerPassPage`, `AdminDashboardPage`, `AdminWorkersPage`, `AdminWorkerPage`, `AdminJournalPage`, `AdminShiftsPage`, `AdminAssistantPage`, `NotFoundPage`, `ForbiddenPage`.

## Владение
F1 — зоны kiosk и worker (их pages/widgets/features), F2 — admin + `shared` + `entities` + `app`. Изменение `shared`/`entities` — ревью F2.

## Команды
```bash
cd subsidy/frontend
npm ci && npm run gen:api
npm run dev                         # http://localhost:5173/admin
npm run lint && npm run typecheck && npm run build
node ../../.agents/skills/frontend-fsd/scripts/fsd-check.mjs src
```
