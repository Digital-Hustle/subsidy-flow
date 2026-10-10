import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowUp, Check, ListChecks, ShieldAlert, Sparkles } from "lucide-react";
import { errorText } from "@/shared/api";
import { Badge, Button, LogoMark, Spinner, Status } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { fadeUp, press, spring, stagger, tween } from "@/shared/config/motion";
import { assistantApi, assistantKeys, type AskAnswer, type Source } from "../api/assistantApi";
import { ThinkingReply, TOOL_LABEL } from "./StreamReply";

const sourceText = (s: Source) => (typeof s === "string" ? s : Object.values(s).filter((v) => v !== null && v !== "").join(" · "));

type Bubble = { id: string; role: string; text: string; sources: Source[]; warn: boolean; tools?: string[] };

/**
 * Помощник по заявке: лента сообщений с анимацией появления, подсказки-карточки, поле ввода — пилюля внизу.
 * История хранится на сервере по пакету; числа в ответе проверяет сервер (validator), клиент только показывает итог.
 */
export const AssistantChat = ({ packageId, compact, className }: { packageId: string; compact?: boolean; className?: string }) => {
  const qc = useQueryClient();
  const [q, setQ] = useState("");
  const [pending, setPending] = useState<string | null>(null);
  const [tools, setTools] = useState<Record<string, string[]>>({});
  const scroller = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const messages = useQuery({ queryKey: assistantKeys.messages(packageId), queryFn: () => assistantApi.messages(packageId) });
  const hints = useQuery({ queryKey: assistantKeys.suggestions(packageId), queryFn: () => assistantApi.suggestions(packageId) });
  const ask = useMutation({
    mutationFn: (question: string) => assistantApi.ask(packageId, question),
    onSuccess: (a: AskAnswer) => {
      setTools((t) => ({ ...t, [a.messageId]: a.tools }));
      void qc.invalidateQueries({ queryKey: assistantKeys.messages(packageId) });
    },
    onSettled: () => { setPending(null); input.current?.focus(); },
  });
  const send = (text = q) => {
    const t = text.trim();
    if (!t || ask.isPending) return;
    setQ("");
    setPending(t);
    ask.mutate(t);
  };

  const items: Bubble[] = (messages.data?.items ?? []).map((m) => ({
    id: m.messageId, role: m.role, text: m.content, sources: m.sources ?? [], warn: !!m.validatorStatus && m.validatorStatus !== "OK", tools: tools[m.messageId],
  }));
  if (pending) items.push({ id: "pending", role: "user", text: pending, sources: [], warn: false });
  const hintItems = hints.data?.items ?? [];
  useEffect(() => { const el = scroller.current; if (el && items.length) el.scrollTo({ top: el.scrollHeight, behavior: "smooth" }); }, [items.length, ask.isPending]);

  return (
    <div className={cn("flex min-h-0 flex-col overflow-hidden rounded-xl bg-card shadow-card", !compact && "h-chat", className)}>
      <div ref={scroller} className="flex-1 overflow-y-auto px-4 py-5 sm:px-5">
        {messages.isPending ? <div className="flex h-full items-center justify-center"><Spinner /></div> : items.length === 0 ? (
          <motion.div variants={stagger(0.05)} initial="hidden" animate="show" className="mx-auto flex min-h-full max-w-2xl flex-col justify-center gap-5">
            <motion.div variants={fadeUp} className="flex flex-col gap-2">
              <h2 className="font-display text-2xl font-semibold tracking-display">Чем помочь?</h2>
              <p className="text-sm text-muted-foreground">Спросите, чего не хватает в заявке или почему правило не пройдено. Ответ строится только по протоколу и данным документов, числа сверяет сервер.</p>
            </motion.div>
            <div className={cn("grid gap-2", !compact && "sm:grid-cols-2")}>
              {hintItems.map((h, i) => (
                <motion.button key={h} type="button" variants={fadeUp} {...press} onClick={() => send(h)} disabled={ask.isPending}
                  className="flex min-h-control-lg items-center gap-3 rounded-md bg-muted px-3.5 py-3 text-left text-sm font-medium outline-none transition-colors duration-fast hover:bg-surface focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-sm bg-card text-brand shadow-xs">{i % 2 ? <ListChecks className="size-4" /> : <Sparkles className="size-4" />}</span>{h}
                </motion.button>
              ))}
            </div>
          </motion.div>
        ) : (
          <div className="mx-auto flex max-w-2xl flex-col gap-5">
            <AnimatePresence initial={false}>
              {items.map((m) => (
                <motion.div key={m.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={spring.soft}
                  className={cn("flex gap-3", m.role === "user" ? "justify-end" : "justify-start")}>
                  {m.role === "user" ? (
                    <div className="ml-10 max-w-md whitespace-pre-wrap rounded-lg rounded-br-xs bg-primary px-4 py-2.5 text-sm text-primary-foreground">{m.text}</div>
                  ) : (<>
                    <LogoMark className="mt-0.5 hidden size-8 sm:flex" />
                    <div className="flex min-w-0 flex-1 flex-col gap-2.5">
                      {!!m.tools?.length && (
                        <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                          {[...new Set(m.tools)].map((t) => <span key={t} className="inline-flex items-center gap-1"><Check className="size-3 text-brand" strokeWidth={3} />{TOOL_LABEL[t] ?? t}</span>)}
                        </div>
                      )}
                      <p className="whitespace-pre-wrap text-sm leading-relaxed">{m.text}</p>
                      {m.warn && <Status tone="warning"><ShieldAlert />числа не подтверждены — проверьте по протоколу</Status>}
                      {m.sources.length > 0 && (
                        <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">Источники:{m.sources.map((s, i) => <Badge key={i}>{sourceText(s)}</Badge>)}</div>
                      )}
                    </div>
                  </>)}
                </motion.div>
              ))}
            </AnimatePresence>
            {ask.isPending && (
              <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ ...spring.soft, opacity: tween.base }} className="flex gap-3" aria-live="polite">
                <LogoMark className="mt-0.5 hidden size-8 sm:flex" />
                <ThinkingReply />
              </motion.div>
            )}
          </div>
        )}
      </div>
      {ask.isError && <p className="px-5 pb-2 text-sm text-danger">{errorText(ask.error)}</p>}
      {items.length > 0 && hintItems.length > 0 && !compact && (
        <div className="flex gap-2 overflow-x-auto px-4 pb-2 sm:px-5">
          {hintItems.map((h) => <Button key={h} size="sm" variant="secondary" className="shrink-0" disabled={ask.isPending} onClick={() => send(h)}>{h}</Button>)}
        </div>
      )}
      <div className="px-3 pb-3 sm:px-4 sm:pb-4">
        <form onSubmit={(e) => { e.preventDefault(); send(); }}
          className="mx-auto flex max-w-2xl items-end gap-2 rounded-lg bg-muted p-1.5 pl-4 ring-1 ring-border transition-shadow duration-fast focus-within:ring-2 focus-within:ring-ring">
          <textarea ref={input} rows={1} value={q} maxLength={1000} onChange={(e) => setQ(e.target.value)} aria-label="Вопрос помощнику"
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }}
            placeholder="Вопрос по заявке…"
            className="field-sizing-content max-h-40 min-h-control-md flex-1 resize-none bg-transparent py-2.5 text-sm outline-none placeholder:text-subtle-foreground" />
          <Button type="submit" size="icon" variant="brand" disabled={!q.trim() || ask.isPending} aria-label="Отправить" className="rounded-md"><ArrowUp /></Button>
        </form>
      </div>
    </div>
  );
};
