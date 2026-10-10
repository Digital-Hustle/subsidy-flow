import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AnimatePresence, motion } from "motion/react";
import { FlaskConical, ShieldOff, UserCheck, UserPlus, Users } from "lucide-react";
import { useSession } from "@/entities/session";
import { STAFF_ROLES, STAFF_ROLE_TITLE, staffApi, staffKeys, type StaffMember, type StaffRole } from "@/entities/staff";
import { CreateStaffDialog } from "@/features/manage-staff";
import { Avatar, Button, Card, Dialog, EmptyState, PageHeader, RowsSkeleton, Select, Status, toast } from "@/shared/ui";
import { agoRu } from "@/shared/lib";
import { errorText } from "@/shared/api";
import { fadeUp, stagger } from "@/shared/config/motion";

type Confirm = { kind: "off" | "on"; m: StaffMember } | null;

/** Сотрудники (старший специалист): пригласить по почте, сменить роль, запретить вход. Вход — по коду из письма. */
export const StaffPage = () => {
  const qc = useQueryClient();
  const { id: me } = useSession();
  const q = useQuery({ queryKey: staffKeys.all, queryFn: staffApi.list });
  const [create, setCreate] = useState(false);
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [last, setLast] = useState<Confirm>(null);
  if (confirm && confirm !== last) setLast(confirm);
  const c = confirm ?? last;

  const act = useMutation({
    mutationFn: (x: NonNullable<Confirm>) => staffApi.update(x.m.id, { active: x.kind === "on" }),
    onSuccess: (_, x) => {
      void qc.invalidateQueries({ queryKey: staffKeys.all });
      setConfirm(null);
      toast.success(x.kind === "on" ? `Вход для ${x.m.email} снова разрешён` : `Вход для ${x.m.email} запрещён, сессии закрыты`);
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const role = useMutation({
    mutationFn: (x: { m: StaffMember; role: StaffRole }) => staffApi.update(x.m.id, { role: x.role }),
    onSuccess: (_, x) => { void qc.invalidateQueries({ queryKey: staffKeys.all }); toast.success(`${x.m.name}: теперь ${STAFF_ROLE_TITLE[x.role].toLowerCase()}`); },
    onError: (e) => toast.error(errorText(e)),
  });
  const items = q.data ?? [];

  return (
    <>
      <PageHeader title="Сотрудники" sub="Вход без паролей: рабочая почта и одноразовый код из письма"
        actions={<Button variant="brand" onClick={() => setCreate(true)}><UserPlus />Пригласить</Button>} />
      {q.isPending ? <Card className="overflow-hidden"><RowsSkeleton rows={4} /></Card> : q.isError ? (
        <EmptyState title="Список не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<Users />} title="Сотрудников нет" text="Пригласите первого — он войдёт по коду из письма" /></Card>
      ) : (
        <Card className="overflow-hidden">
          <motion.ul variants={stagger(0.03)} initial="hidden" animate="show">
            <AnimatePresence initial={false}>
              {items.map((m) => {
                const name = m.name || m.email;
                const self = m.id === me;
                const enabled = m.status !== "DISABLED";
                const seen = m.lastSeen ? (typeof m.lastSeen === "number" ? (m.lastSeen < 1e12 ? m.lastSeen * 1000 : m.lastSeen) : Date.parse(m.lastSeen)) : null;
                return (
                  <motion.li key={m.id} layout="position" variants={fadeUp} exit="exit"
                    className="flex flex-col gap-3 border-t border-border px-4 py-3.5 first:border-t-0 sm:flex-row sm:items-center sm:gap-4 sm:px-6">
                    <div className="flex min-w-0 flex-1 items-center gap-3">
                      <Avatar name={name} className="size-10" />
                      <div className="min-w-0">
                        <div className="truncate font-medium">{name}{self && <span className="text-muted-foreground"> · это вы</span>}</div>
                        <div className="truncate text-xs text-muted-foreground">{m.email}{seen ? ` · был ${agoRu(seen)}` : m.status === "INVITED" ? " · ещё не входил" : ""}</div>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-1.5">
                      {self || m.demo ? <Status tone={m.role === "SENIOR_SPECIALIST" ? "success" : m.role === "AUDITOR" ? "info" : "neutral"}>{STAFF_ROLE_TITLE[m.role]}</Status>
                        : <Select size="sm" aria-label={`Роль: ${name}`} className="w-52" value={m.role} options={STAFF_ROLES.map((r) => ({ value: r, label: STAFF_ROLE_TITLE[r] }))} onChange={(r) => r !== m.role && role.mutate({ m, role: r })} />}
                      {m.demo && <Status tone="info"><FlaskConical />демо</Status>}
                      {m.status === "INVITED" && <Status tone="warning">приглашён</Status>}
                      {!enabled && <Status tone="danger">вход запрещён</Status>}
                    </div>
                    <div className="flex shrink-0 gap-1.5">
                      {!self && !m.demo && (enabled
                        ? <Button size="sm" variant="quiet" onClick={() => setConfirm({ kind: "off", m })}><ShieldOff />Запретить</Button>
                        : <Button size="sm" variant="quiet" onClick={() => setConfirm({ kind: "on", m })}><UserCheck />Разрешить</Button>)}
                    </div>
                  </motion.li>
                );
              })}
            </AnimatePresence>
          </motion.ul>
        </Card>
      )}

      <CreateStaffDialog open={create} onClose={() => setCreate(false)} />
      <Dialog open={!!confirm} onClose={() => setConfirm(null)} onClosed={() => setLast(null)}
        title={c?.kind === "off" ? `Запретить вход ${c.m.email}?` : `Разрешить вход ${c?.m.email}?`}
        description={c?.kind === "off" ? "Сотрудник выйдет на всех устройствах и не сможет войти, пока вы не разрешите снова" : "Сотрудник сможет войти по коду из письма"}
        footer={<Button block size="lg" variant={c?.kind === "off" ? "danger" : "primary"} disabled={act.isPending} onClick={() => c && act.mutate(c)}>
          {act.isPending ? "Сохраняем…" : c?.kind === "off" ? "Запретить вход" : "Разрешить вход"}</Button>}>
        {null}
      </Dialog>
    </>
  );
};
