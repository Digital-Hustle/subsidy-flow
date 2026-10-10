import { Link } from "react-router";
import { Compass } from "lucide-react";
import { Button, EmptyState } from "@/shared/ui";
import { routes } from "@/shared/const/router";

export const NotFoundPage = () => (
  <EmptyState icon={<Compass />} title="Страница не найдена" text="Возможно, ссылка устарела" className="min-h-svh" action={<Link to={routes.home}><Button>На главную</Button></Link>} />
);
