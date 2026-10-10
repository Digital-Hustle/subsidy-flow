import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { UserPlus, Users } from "lucide-react";
import { staffApi, staffKeys, can, roleLabel, useSession, ROLES, type Role, type StaffUser } from "@/entities/session";
import { errorText } from "@/shared/api";
import { Avatar, Button, Card, Dialog, EmptyState, Field, Input, PageHeader, RowsSkeleton, Select, Status, Switch, toast } from "@/shared/ui";
import { fadeUp, stagger } from "@/shared/config/motion";
import { agoRu } from "@/shared/lib";

const TONE = { ACTIVE: "success", INVITED: "info", DISABLED: "neutral" } as const;
const STATUS = { ACTIVE: "работает", INVITED: "приглашён", DISABLED: "отключён" } as const;

/** Приглашение: сотрудник войдёт сам — по коду, который придёт на указанную почту. */
const Invite = () => {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("SPECIALIST");
  const add = useMutation({
    mutationFn: () => staffApi.invite({ name: name.trim(), email: email.trim().toLowerCase(), role }),
    onSuccess: (u) => { setOpen(false); setName(""); setEmail(""); toast.success(`${u.name} приглашён — код придёт на ${u.email}`); void qc.invalidateQueries({ queryKey: staffKeys.list }); },
  });
  return (
    <>
      <Button variant="brand" onClick={() => setOpen(true)}><UserPlus />Пригласить</Button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Новый сотрудник" description="Пароля нет: сотрудник войдёт по одноразовому коду, который придёт на рабочую почту"
        footer={<Button disabled={!name.trim() || !email.includes("@") || add.isPending} onClick={() => add.mutate()}>{add.isPending ? "Приглашаем…" : "Пригласить"}</Button>}>
        <div className="flex flex-col gap-4">
          <Field label="Имя и фамилия"><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Анна Смирнова" /></Field>
          <Field label="Рабочая почта"><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.ru" /></Field>
          <Field label="Роль"><Select value={role} onChange={setRole} options={ROLES.map((r) => ({ value: r, label: roleLabel(r) }))} /></Field>
          {add.isError && <p className="text-sm text-danger">{errorText(add.error)}</p>}
        </div>
      </Dialog>
    </>
  );
};

/** Строка сотрудника: роль и доступ меняются на месте, сервер не даст снять последнего старшего. */
const Row = ({ u }: { u: StaffUser }) => {
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: (body: { role?: Role; active?: boolean }) => staffApi.update(u.id, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: staffKeys.list }),
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <motion.div variants={fadeUp}>
      <Card className="flex flex-wrap items-center gap-3 p-4">
        <Avatar name={u.name} className="size-10" />
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-2"><span className="truncate font-medium">{u.name}</span>{u.demo && <Status tone="info">демо</Status>}</span>
          <span className="block truncate text-sm text-muted-foreground">{u.email}{u.lastSeen ? ` · заходил ${agoRu(u.lastSeen)}` : ""}</span>
        </span>
        <Status tone={TONE[u.status]}>{STATUS[u.status]}</Status>
        <Select size="sm" className="w-auto" value={u.role} onChange={(role) => save.mutate({ role })} aria-label={`Роль: ${u.name}`} options={ROLES.map((r) => ({ value: r, label: roleLabel(r) }))} />
        <Switch checked={u.status !== "DISABLED"} onChange={(v) => save.mutate({ active: v })} label={`Доступ: ${u.name}`} />
      </Card>
    </motion.div>
  );
};

/** FE-UI-04: сотрудники рабочего места — приглашение, роль, доступ. Видит и меняет только старший специалист. */
export const StaffPage = () => {
  const { roles } = useSession();
  const allowed = can(roles, "staff");
  const q = useQuery({ queryKey: staffKeys.list, queryFn: staffApi.list, enabled: allowed });
  if (!allowed) return <EmptyState icon={<Users />} title="Раздел для старшего специалиста" text="Список сотрудников и права доступны только старшему специалисту" className="py-24" />;
  return (
    <>
      <PageHeader title="Сотрудники" sub="Кто работает в системе, с какой ролью и доступом" actions={<Invite />} />
      {q.isPending ? <RowsSkeleton rows={4} /> : q.isError ? (
        <EmptyState title="Список не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !q.data.items.length ? <Card><EmptyState icon={<Users />} title="Сотрудников нет" className="py-16" /></Card> : (
        <motion.div variants={stagger(0.05)} initial="hidden" animate="show" className="flex flex-col gap-3">
          {q.data.items.map((u) => <Row key={u.id} u={u} />)}
        </motion.div>
      )}
    </>
  );
};
