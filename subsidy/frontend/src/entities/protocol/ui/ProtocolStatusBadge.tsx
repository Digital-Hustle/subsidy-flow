import { Status } from "@/shared/ui";
import { protocolView } from "../model/views";

export const ProtocolStatusBadge = ({ status, className }: { status?: string | null; className?: string }) => {
  const v = protocolView(status);
  return <Status tone={v.tone} className={className}>{v.label}</Status>;
};
