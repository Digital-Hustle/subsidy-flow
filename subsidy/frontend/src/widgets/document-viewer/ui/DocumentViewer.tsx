import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, RotateCw, ZoomIn, ZoomOut } from "lucide-react";
import { packageApi, fieldText, fieldLabel, reasonText, StageBadge, type DocumentResult } from "@/entities/package";
import { ChangeTypeButton } from "@/features/change-type";
import { CorrectFieldButton } from "@/features/correct-field";
import { Button, Card, CardContent, EmptyState, Skeleton, Spinner, Status } from "@/shared/ui";
import { cn } from "@/shared/lib";

const ZOOMS = ["w-1/2", "w-3/4", "w-full"] as const;
const ROTATE = ["rotate-0", "rotate-90", "rotate-180", "-rotate-90"] as const;
const LOW_CONFIDENCE = 0.8;
const PERCENT = 100;

const checkTone = (s: string) => (s === "FAILED" ? "danger" : s === "WARNING" ? "warning" : "success");

/** Страница файла как PNG (blob из package), URL освобождается при смене страницы. */
const PageImage = ({ fileId, page, zoom, turn }: { fileId: string; page: number; zoom: number; turn: number }) => {
  const [src, setSrc] = useState<string | null>(null);
  const q = useQuery({ queryKey: ["package", "page", fileId, page], queryFn: ({ signal }) => packageApi.page(fileId, page, signal), staleTime: Infinity });
  useEffect(() => {
    if (!q.data) return;
    const u = URL.createObjectURL(q.data);
    setSrc(u);
    return () => URL.revokeObjectURL(u);
  }, [q.data]);
  if (q.isError) return <EmptyState title="Страница не загрузилась" text="Файл ещё разбирается или недоступен" />;
  if (!src) return <Skeleton className="aspect-3/4 w-full" />;
  return <img src={src} alt={`Страница ${page}`} className={cn("mx-auto rounded-md shadow-card transition-transform duration-fast", ZOOMS[zoom], ROTATE[turn])} />;
};

/** FE-PK-04/05: просмотр документа (страницы, зум, поворот) и извлечённые поля с уверенностью и проверками. */
export const DocumentViewer = ({ packageId, doc, typeName }: { packageId: string; doc: DocumentResult; typeName: string }) => {
  const [page, setPage] = useState(doc.fromPage);
  const [zoom, setZoom] = useState(2);
  const [turn, setTurn] = useState(0);
  useEffect(() => { setPage(doc.fromPage); }, [doc.documentId, doc.fromPage]);
  const reason = reasonText(doc.reasonCode);

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <Card>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <Button size="icon" variant="ghost" aria-label="Предыдущая страница" disabled={page <= doc.fromPage} onClick={() => setPage(page - 1)}><ChevronLeft /></Button>
            <span className="text-sm tabular-nums">стр. {page} из {doc.fromPage}–{doc.toPage}</span>
            <Button size="icon" variant="ghost" aria-label="Следующая страница" disabled={page >= doc.toPage} onClick={() => setPage(page + 1)}><ChevronRight /></Button>
            <span className="flex-1" />
            <Button size="icon" variant="ghost" aria-label="Уменьшить" disabled={zoom === 0} onClick={() => setZoom(zoom - 1)}><ZoomOut /></Button>
            <Button size="icon" variant="ghost" aria-label="Увеличить" disabled={zoom === ZOOMS.length - 1} onClick={() => setZoom(zoom + 1)}><ZoomIn /></Button>
            <Button size="icon" variant="ghost" aria-label="Повернуть" onClick={() => setTurn((turn + 1) % ROTATE.length)}><RotateCw /></Button>
          </div>
          <div className="overflow-auto"><PageImage fileId={doc.fileId} page={page} zoom={zoom} turn={turn} /></div>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="flex-1 font-semibold">{typeName}</h3>
            <StageBadge stage={doc.stage} />
            <ChangeTypeButton packageId={packageId} documentId={doc.documentId} current={doc.type} />
          </div>
          {doc.typeConfidence !== null && <p className="text-xs text-muted-foreground">Уверенность в виде: {Math.round(doc.typeConfidence * PERCENT)}%{doc.model ? ` · модель ${doc.model}` : ""}</p>}
          {reason && <Status tone="warning">{reason}</Status>}
          {!doc.fields ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner className="size-4" />Поля ещё извлекаются</div>
          ) : !doc.fields.length ? (
            <p className="text-sm text-muted-foreground">У этого вида нет полей для извлечения</p>
          ) : (
            <ul className="flex flex-col divide-y divide-border">
              {doc.fields.map((f) => {
                const low = f.confidence !== null && f.confidence < LOW_CONFIDENCE;
                return (
                  <li key={f.name} className="flex items-start gap-2 py-2">
                    <div className="min-w-0 flex-1">
                      <div className="text-xs text-muted-foreground">{fieldLabel(f.name)}</div>
                      <div className={cn("break-words", low && "text-warning")}>{fieldText(f.value)}</div>
                      {f.raw && f.raw !== fieldText(f.value) && <div className="text-xs text-muted-foreground">на скане: {f.raw}</div>}
                      {f.checks.filter((c) => c.status !== "PASSED" && c.status !== "OK").map((c) => (
                        <Status key={c.code} tone={checkTone(c.status)} className="mt-1">{c.message}</Status>
                      ))}
                    </div>
                    {f.confidence !== null && <span className={cn("text-xs tabular-nums", low ? "text-warning" : "text-muted-foreground")}>{Math.round(f.confidence * PERCENT)}%</span>}
                    {typeof f.value !== "object" || f.value === null
                      ? <CorrectFieldButton packageId={packageId} documentId={doc.documentId} field={f.name} label={fieldLabel(f.name)} current={f.value === null ? "" : String(f.value)} />
                      : null}
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
};
