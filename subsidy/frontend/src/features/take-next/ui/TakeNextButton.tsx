import { useNavigate } from "react-router";
import { useMutation } from "@tanstack/react-query";
import { SkipForward } from "lucide-react";
import { reviewApi } from "@/entities/application";
import { Button, toast, type ButtonProps } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { routes } from "@/shared/const/router";

/** Взять следующую свободную заявку (сервер назначает её мне, старшие — первыми). Горячая клавиша N — на странице. */
export const useTakeNext = () => {
  const nav = useNavigate();
  return useMutation({
    mutationFn: reviewApi.next,
    onSuccess: (r) => {
      if (r?.packageId) nav(routes.operatorApplication(r.packageId));
      else toast.info("Свободных заявок нет — очередь пуста");
    },
    onError: (e) => toast.error(errorText(e)),
  });
};

export const TakeNextButton = ({ size = "md", variant = "brand" }: { size?: ButtonProps["size"]; variant?: ButtonProps["variant"] }) => {
  const m = useTakeNext();
  return <Button size={size} variant={variant} disabled={m.isPending} onClick={() => m.mutate()}><SkipForward />{m.isPending ? "Берём…" : "Следующая заявка"}<kbd className="ml-1 rounded-xs bg-white/20 px-1.5 text-xs max-md:hidden">N</kbd></Button>;
};
