import { Status } from "@/shared/ui";
import { stageView } from "../model/views";

export const StageBadge = ({ stage, className }: { stage?: string | null; className?: string }) => {
  const v = stageView(stage);
  return <Status tone={v.tone} className={className}>{v.label}</Status>;
};
