import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Check, FlaskConical, Minus, Server, Users } from "lucide-react";
import { Link } from "react-router";
import { getMode, permsOf, roleLabel, useSession, ROLES, type Perm } from "@/entities/session";
import { env } from "@/shared/config/env";
import { Card, CardHeader, CardTitle, PageHeader, Status, ThemePicker } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";
import { fadeUp, stagger } from "@/shared/config/motion";

const COLUMNS: { id: Perm; label: string }[] = [
  { id: "work", label: "Пакеты и правки" },
  { id: "decide", label: "Решения" },
  { id: "programs", label: "Программы" },
  { id: "reprocess", label: "Переразбор" },
  { id: "stats", label: "Отчёты" },
  { id: "staff", label: "Сотрудники" },
];
const ROLE_TEXT: Record<string, string> = {
  SPECIALIST: "Ведёт пакеты: загружает документы, правит данные, видит протокол",
  SENIOR_SPECIALIST: "Всё то же плюс решения по пакетам, программы, переразбор и сотрудники",
  AUDITOR: "Только чтение: протоколы, история, отчёты",
};

/** FE-UI-04: настройки рабочего места — оформление, режим стенда, подключение и матрица ролей. */
export const SettingsPage = () => {
  const { role, mode } = useSession();
  const stand = useQuery({ queryKey: ["package", "auth", "mode"], queryFn: getMode });
  const demo = stand.data?.mode === "demo" || mode === "demo";
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Настройки" sub="Оформление, режим стенда, подключение и права ролей" />
      <motion.div variants={stagger()} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Оформление</CardTitle></CardHeader>
          <div className="flex flex-col gap-4 p-4 sm:p-6">
            <p className="text-pretty text-sm text-muted-foreground">Светлая и тёмная тема; «как в системе» следует за настройкой устройства. Выбор запоминается в этом браузере.</p>
            <ThemePicker />
          </div>
        </Card></motion.div>

        <motion.div variants={fadeUp}><Card>
          <CardHeader className="flex flex-row items-center gap-2"><CardTitle>Режим стенда</CardTitle><Status tone={demo ? "info" : "success"}>{demo ? "демо" : "боевой"}</Status></CardHeader>
          <div className="flex flex-col gap-4 p-4 sm:p-6">
            <p className="text-pretty text-sm text-muted-foreground">
              {demo
                ? "Демо-стенд: на странице входа есть тестовые сотрудники, код приходит прямо на экран, а код 000000 пускает любого заведённого сотрудника. Так удобно показывать и отлаживать, но так нельзя работать с настоящими документами."
                : "Боевой режим: тестовые сотрудники отключены, код приходит только письмом на рабочую почту. Войти может лишь тот, кого пригласил старший специалист."}
            </p>
            {demo && stand.data?.demo.length ? (
              <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
                <span className="text-xs font-medium text-muted-foreground">Демо-сотрудники</span>
                {stand.data.demo.map((u) => (
                  <span key={u.id} className="flex flex-wrap items-center gap-2 text-sm"><FlaskConical className="size-4 text-subtle-foreground" /><span className="font-medium">{u.name}</span><span className="text-muted-foreground">{roleLabel(u.role)} · {u.email}</span></span>
                ))}
              </div>
            ) : null}
            <p className="text-pretty text-xs text-muted-foreground">Режим задаёт администратор стенда переменной <span className="font-mono">STAND_MODE</span> (docs/DEPLOY.md); из интерфейса его не переключить — иначе боевой стенд можно было бы «уронить» в демо одним нажатием.</p>
          </div>
        </Card></motion.div>

        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Подключение</CardTitle></CardHeader>
          <div className="flex flex-col gap-3 p-4 sm:p-6">
            <div className="flex items-start gap-3 text-sm">
              <Server className="mt-0.5 size-5 shrink-0 text-subtle-foreground" />
              <span className="min-w-0"><span className="block font-medium">Адрес API</span><span className="block truncate text-muted-foreground">{env.apiBase || `${location.origin} (тот же адрес, через Caddy)`}</span></span>
            </div>
            <p className="text-pretty text-xs text-muted-foreground">Фронт можно подключить к стороннему или локальному API без пересборки: поле <span className="font-mono">apiBase</span> в файле <span className="font-mono">/config.js</span> рядом со статикой.</p>
          </div>
        </Card></motion.div>

        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Роли и доступ</CardTitle></CardHeader>
          <div className="flex flex-col gap-4 p-4 sm:p-6">
            <p className="text-pretty text-sm text-muted-foreground">Каждая роль видит свои разделы; право на действие всё равно проверяет сервер. Чтобы посмотреть рабочее место глазами другой роли на демо-стенде, войдите другим демо-сотрудником.</p>
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full text-sm">
                <thead className="bg-muted text-left text-muted-foreground"><tr><th className="px-3 py-2 font-medium">Роль</th>{COLUMNS.map((c) => <th key={c.id} className="whitespace-nowrap px-2 py-2 text-center font-medium">{c.label}</th>)}</tr></thead>
                <tbody>{ROLES.map((r) => (
                  <tr key={r} className={cn("border-t border-border", r === role && "bg-accent text-accent-foreground")}>
                    <td className="px-3 py-2.5"><div className="whitespace-nowrap font-medium">{roleLabel(r)}</div><div className="hidden text-xs text-muted-foreground md:block">{ROLE_TEXT[r]}</div></td>
                    {COLUMNS.map((c) => <td key={c.id} className="px-2 py-2.5 text-center">{permsOf(r).includes(c.id) ? <Check className="mx-auto size-4 text-success" aria-label="есть" /> : <Minus className="mx-auto size-4 text-subtle-foreground" aria-label="нет" />}</td>)}
                  </tr>
                ))}</tbody>
              </table>
            </div>
            {role === "SENIOR_SPECIALIST" && <Link to={routes.staff} className="inline-flex items-center gap-1.5 self-start text-sm font-medium underline-offset-4 hover:underline"><Users className="size-4" />Пригласить сотрудника и назначить роли →</Link>}
          </div>
        </Card></motion.div>
      </motion.div>
    </div>
  );
};
