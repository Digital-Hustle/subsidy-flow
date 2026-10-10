import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ChevronLeft, ChevronRight, RotateCw, ZoomIn, ZoomOut, ImageOff, Lock } from "lucide-react";
import type { DocFile, DocumentCard } from "@/entities/application";
import { ApiError, apiBlob } from "@/shared/api";
import { Button, EmptyState, Skeleton } from "@/shared/ui";
import { spring } from "@/shared/config/motion";

export type Highlight = { page?: number | null; bbox?: number[] | null } | null;

const ZOOMS = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];

/** Страница документа → файл и страница внутри файла (документ — диапазон страниц файла, offset = fromPage - 1). */
const locate = (files: DocFile[], n: number) => {
  let left = n;
  for (const f of files) {
    if (left <= f.pages) return { file: f, page: f.offset + left };
    left -= f.pages;
  }
  return files[0] ? { file: files[0], page: files[0].offset + 1 } : null;
};

/**
 * Оригинал документа: превью страниц — только через package /files/{id}/pages/{n}.png (blob с токеном, без публичных URL),
 * URL.revokeObjectURL при уходе. Зум, поворот, переход к странице доказательства из протокола.
 */
export const DocumentViewer = ({ doc, highlight }: { doc: DocumentCard; highlight: Highlight }) => {
  const files = doc.files;
  const total = Math.max(1, files.reduce((s, f) => s + f.pages, 0));
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState(2);
  const [rot, setRot] = useState(0);
  const target = highlight?.page;
  useEffect(() => { if (target && target <= total) setPage(target); }, [target, total]);
  useEffect(() => { setPage(1); setRot(0); }, [doc.id, doc.files[0]?.version]);
  const loc = locate(files, Math.min(page, total));
  const q = useQuery({
    queryKey: ["file", loc?.file.id, loc?.file.version, loc?.page],
    queryFn: ({ signal }) => apiBlob("package", `/files/${loc!.file.id}/pages/${loc!.page}.png?version=${loc!.file.version ?? 1}`, signal),
    enabled: !!loc,
    staleTime: Infinity,
    gcTime: 60_000,
    // Превью страницы рендерится по запросу — пока файл в разборе, сервер может ответить 404/409: пробуем ещё пару раз
    retry: (n, e) => (e instanceof ApiError && (e.status === 404 || e.status === 409 || e.status >= 500) ? n < 2 : false),
    retryDelay: 1500,
  });
  const forbidden = q.error instanceof ApiError && q.error.status === 403;
  const [preview, setPreview] = useState<{ blob: Blob; url: string } | null>(null);
  useEffect(() => {
    if (!q.data) return;
    const url = URL.createObjectURL(q.data);
    setPreview({ blob: q.data, url });
    return () => URL.revokeObjectURL(url);
  }, [q.data]);
  const src = preview?.blob === q.data ? preview?.url : null;

  if (!files.length) return <EmptyState icon={<ImageOff />} title="Файлов нет" text="Файл удалён или ещё загружается" />;
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex flex-wrap items-center gap-1 border-b border-border px-2 py-1.5">
        <Button size="icon-sm" variant="quiet" aria-label="Предыдущая страница" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}><ChevronLeft /></Button>
        <span className="min-w-16 text-center text-sm tabular-nums text-muted-foreground" title={doc.fileName}>{page} / {total}</span>
        <Button size="icon-sm" variant="quiet" aria-label="Следующая страница" disabled={page >= total} onClick={() => setPage((p) => p + 1)}><ChevronRight /></Button>
        <span className="mx-1 h-5 w-px bg-border" />
        <Button size="icon-sm" variant="quiet" aria-label="Уменьшить" disabled={zoom <= 0} onClick={() => setZoom((z) => z - 1)}><ZoomOut /></Button>
        <span className="min-w-12 text-center text-sm tabular-nums text-muted-foreground">{Math.round(ZOOMS[zoom] * 100)}%</span>
        <Button size="icon-sm" variant="quiet" aria-label="Увеличить" disabled={zoom >= ZOOMS.length - 1} onClick={() => setZoom((z) => z + 1)}><ZoomIn /></Button>
        <Button size="icon-sm" variant="quiet" aria-label="Повернуть" onClick={() => setRot((r) => (r + 90) % 360)}><RotateCw /></Button>
      </div>
      <div className="min-h-0 flex-1 overflow-auto bg-muted p-4">
        {q.isPending ? <Skeleton className="mx-auto h-96 w-full max-w-xl" />
          : forbidden ? <EmptyState icon={<Lock />} title="Нет доступа к оригиналу" text="Оригиналы файлов видят специалисты с ролью — обратитесь к старшему" />
          : q.isError || !src ? <EmptyState icon={<ImageOff />} title="Страница не открылась" action={<Button size="sm" variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
          : (
            <motion.div animate={{ rotate: rot }} transition={spring.soft} className="relative mx-auto origin-center" style={{ width: `${ZOOMS[zoom] * 100}%` }}>
              <img src={src} alt={`Страница ${page}`} className="block w-full select-none rounded-sm bg-card shadow-card" draggable={false} />
            </motion.div>
          )}
      </div>
    </div>
  );
};
