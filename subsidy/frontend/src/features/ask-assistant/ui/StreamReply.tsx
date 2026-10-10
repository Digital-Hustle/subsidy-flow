import { motion } from "motion/react";
import { cn } from "@/shared/lib";
import { duration, ease } from "@/shared/config/motion";

/** Инструменты помощника (assistant → rules): подписи для шагов и чипов «что смотрел». */
export const TOOL_LABEL: Record<string, string> = {
  get_protocol: "Смотрю протокол проверки",
  get_missing: "Проверяю комплект документов",
  get_fields: "Читаю поля документов",
  get_anomalies: "Ищу аномалии в суммах",
};

/** Текст с бегущим бликом — «модель думает». Две ключевые точки, бесконечный линейный проход. */
export const Shimmer = ({ children, className }: { children: React.ReactNode; className?: string }) => (
  <motion.span className={cn("bg-linear-to-r from-muted-foreground via-foreground to-muted-foreground bg-clip-text text-transparent", className)}
    style={{ backgroundSize: "200% 100%" }} animate={{ backgroundPosition: ["100% 0%", "-100% 0%"] }}
    transition={{ duration: duration.loop * 0.75, ease: ease.linear, repeat: Infinity }}>{children}</motion.span>
);

/** Ответ ещё готовится: пульсирующая точка и подпись с бликом. Ответ приходит целиком — шаги не выдумываем. */
export const ThinkingReply = () => (
  <div className="flex min-w-0 flex-1 items-center gap-2 py-1 text-sm">
    <span className="flex size-5 shrink-0 items-center justify-center">
      <motion.span className="size-2 rounded-full bg-brand" animate={{ scale: [1, 1.5], opacity: [1, 0.35] }}
        transition={{ duration: duration.slow * 1.6, ease: ease.inOut, repeat: Infinity, repeatType: "reverse" }} />
    </span>
    <Shimmer>Смотрю протокол и документы заявки…</Shimmer>
  </div>
);
