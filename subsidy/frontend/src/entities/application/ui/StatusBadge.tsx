import { Status } from "@/shared/ui";
import { applicantStatusView, statusView } from "../model/views";

export const AppStatusBadge = ({ status, className, applicant = false }: { status?: string | null; className?: string; applicant?: boolean }) => {
  const v = applicant ? applicantStatusView(status) : statusView(status);
  return <Status tone={v.tone} className={className}>{v.label}</Status>;
};
