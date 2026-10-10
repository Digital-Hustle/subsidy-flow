import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bot, Send, ShieldAlert } from "lucide-react";
import { errorText } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, Spinner, Status, Textarea } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { assistantApi, assistantKeys, type Source } from "../api/assistantApi";

const sourceText = (s: Source) => (typeof s === "string" ? s : Object.values(s).filter((v) => v !== null && v !== "").join(" · "));

/** FE-SP-10: чат помощника по пакету. Числа в ответе проверяет сервер (validator) — клиент только показывает итог. */
export const AssistantChat = ({ packageId }: { packageId: string }) => {
  const qc = useQueryClient();
  const [q, setQ] = useState("");
  const messages = useQuery({ queryKey: assistantKeys.messages(packageId), queryFn: () => assistantApi.messages(packageId) });
  const hints = useQuery({ queryKey: assistantKeys.suggestions(packageId), queryFn: () => assistantApi.suggestions(packageId) });
  const ask = useMutation({
    mutationFn: (question: string) => assistantApi.ask(packageId, question),
    onSuccess: () => { setQ(""); void qc.invalidateQueries({ queryKey: assistantKeys.messages(packageId) }); },
  });
  const send = (text: string) => { const t = text.trim(); if (t && !ask.isPending) ask.mutate(t); };
  const items = messages.data?.items ?? [];

  return (
    <Card>
      <CardHeader><CardTitle className="flex items-center gap-2"><Bot className="size-5" />Помощник по пакету</CardTitle></CardHeader>
      <CardContent className="flex flex-col gap-3">
        {messages.isPending ? <Spinner /> : !items.length ? (
          <p className="text-sm text-muted-foreground">Спросите, чего не хватает в пакете или почему правило не пройдено. Ответ строится только по протоколу и данным документов.</p>
        ) : (
          <ul className="flex max-h-96 flex-col gap-2 overflow-y-auto">
            {items.map((m) => (
              <li key={m.messageId} className={cn("rounded-lg px-3 py-2 text-sm", m.role === "user" ? "self-end bg-primary text-primary-foreground" : "self-start bg-surface")}>
                <p className="whitespace-pre-wrap">{m.content}</p>
                {!!m.sources?.length && <p className="mt-1 text-xs opacity-80">Источники: {m.sources.map(sourceText).join("; ")}</p>}
                {m.validatorStatus && m.validatorStatus !== "OK" && <Status tone="warning" className="mt-1"><ShieldAlert />числа не подтверждены — проверьте по протоколу</Status>}
              </li>
            ))}
          </ul>
        )}
        {ask.isPending && <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner className="size-4" />Помощник смотрит протокол…</div>}
        {ask.isError && <p className="text-sm text-danger">{errorText(ask.error)}</p>}
        {!!hints.data?.items.length && (
          <div className="flex flex-wrap gap-2">
            {hints.data.items.map((h) => <Button key={h} size="sm" variant="secondary" disabled={ask.isPending} onClick={() => send(h)}>{h}</Button>)}
          </div>
        )}
        <form className="flex items-end gap-2" onSubmit={(e) => { e.preventDefault(); send(q); }}>
          <Textarea rows={2} value={q} maxLength={1000} onChange={(e) => setQ(e.target.value)} placeholder="Вопрос по пакету" aria-label="Вопрос помощнику"
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(q); } }} />
          <Button type="submit" size="icon" aria-label="Отправить" disabled={!q.trim() || ask.isPending}><Send /></Button>
        </form>
      </CardContent>
    </Card>
  );
};
