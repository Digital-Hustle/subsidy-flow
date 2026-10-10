import { useQuery } from "@tanstack/react-query";
import { Cpu } from "lucide-react";
import { packageApi, packageKeys } from "@/entities/package";
import { Status } from "@/shared/ui";

const POLL_MS = 30_000;

/** Доступна ли модель разбора — специалист видит, почему документы ждут (WAITING_MODEL). */
export const ModelStatusBadge = ({ className }: { className?: string }) => {
  const q = useQuery({ queryKey: packageKeys.model, queryFn: packageApi.model, refetchInterval: POLL_MS });
  if (!q.data) return null;
  const ok = q.data.reachable;
  return (
    <Status tone={ok ? "success" : "danger"} className={className} title={q.data.error ?? `${q.data.provider ?? ""} ${q.data.model ?? ""}`}>
      <Cpu />{ok ? `Модель: ${q.data.model ?? "на связи"}` : "Модель недоступна"}
    </Status>
  );
};
