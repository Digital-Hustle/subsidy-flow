import { Spinner, Status } from "@/shared/ui";
import { PACKAGE_STATUS, PROTOCOL_STATUS, stageView } from "../model/views";
import type { PackageStatus, ProtocolStatus } from "../model/types";

export const PackageStatusBadge = ({ status }: { status: PackageStatus }) => {
  const v = PACKAGE_STATUS[status] ?? { label: status, tone: "neutral" as const };
  return <Status tone={v.tone}>{v.label}</Status>;
};

export const ProtocolStatusBadge = ({ status }: { status: ProtocolStatus | null | undefined }) => {
  if (!status) return <Status>Проверка не завершена</Status>;
  const v = PROTOCOL_STATUS[status];
  return <Status tone={v.tone}>{v.label}</Status>;
};

export const StageBadge = ({ stage }: { stage: string }) => {
  const v = stageView(stage);
  return <Status tone={v.tone}>{v.busy && <Spinner className="size-3 border" />}{v.label}</Status>;
};
