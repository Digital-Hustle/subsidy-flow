import { useRouteError } from "react-router";
import { RefreshCw, TriangleAlert } from "lucide-react";
import { Button, EmptyState } from "@/shared/ui";

/** Глобальная граница ошибок: «Что-то пошло не так» + перезагрузка; подробности — только в консоль. */
export const ErrorScreen = () => {
  const e = useRouteError();
  console.error(e);
  return <EmptyState icon={<TriangleAlert />} title="Что-то пошло не так" text="Обновите страницу — введённые данные на сервере сохранены" className="min-h-svh"
    action={<Button onClick={() => location.reload()}><RefreshCw />Перезагрузить</Button>} />;
};
