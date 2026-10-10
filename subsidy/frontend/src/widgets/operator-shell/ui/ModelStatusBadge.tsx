import { useQuery } from "@tanstack/react-query";
import { Cpu } from "lucide-react";
import { reviewApi, reviewKeys } from "@/entities/application";
import { Status } from "@/shared/ui";

const POLL_MS = 30_000;

/** FR-46: доступна ли модель разбора — специалист видит, почему документы ждут. */
export const ModelStatusBadge = ({ className }: { className?: string }) => {
  const q = useQuery({ queryKey: reviewKeys.model, queryFn: reviewApi.model, refetchInterval: POLL_MS });
  if (!q.data) return null;
  const ok = q.data.reachable;
  return (
    <Status tone={ok ? "success" : "danger"} className={className} title={q.data.error ?? `${q.data.provider ?? ""} ${q.data.model ?? ""}`}>
      <Cpu />{ok ? `Модель: ${q.data.model ?? "на связи"}` : "Модель недоступна"}
    </Status>
  );
};
