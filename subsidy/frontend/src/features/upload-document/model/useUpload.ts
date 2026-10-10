import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { applicantApi, applicantKeys } from "@/entities/application";
import { ApiError, errorText } from "@/shared/api";
import { env } from "@/shared/config/env";

export type UploadError = { text: string; hint?: string };
type Vars = { file: File; slotType?: string; docId?: string; mode?: "add" | "replace" };


/**
 * Загрузка в пункт, добавление страниц, замена. Отказ сервера — не toast, а состояние пункта (текст и подсказка
 * из problem+json). Клиентская проверка размера — только подсказка; формат решает сервер по байтам (PDF, DOCX, XLSX, JPG/PNG/TIFF/HEIC, ZIP).
 */
export const useUpload = (applicationId: string) => {
  const qc = useQueryClient();
  const [errors, setErrors] = useState<Record<string, UploadError | undefined>>({});
  const [warnings, setWarnings] = useState<Record<string, string[] | undefined>>({});
  const key = (v: Vars) => v.docId ?? v.slotType ?? "extra";
  const m = useMutation({
    mutationFn: async (v: Vars) => (v.docId ? applicantApi.addFile(applicationId, v.docId, v.file, v.mode ?? "add") : applicantApi.upload(applicationId, v.file, v.slotType)),
    onMutate: (v) => setErrors((s) => ({ ...s, [key(v)]: undefined })),
    onSuccess: (r, v) => {
      setWarnings((s) => ({ ...s, [key(v)]: r?.warnings?.length ? r.warnings : undefined }));
      void qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) });
    },
    onError: (e, v) => setErrors((s) => ({ ...s, [key(v)]: { text: errorText(e), hint: e instanceof ApiError ? e.hint : "Проверьте связь и повторите" } })),
  });
  return {
    upload: (v: Vars) => {
      if (v.file.size > env.uploadMaxMb * 1024 * 1024) { setErrors((s) => ({ ...s, [key(v)]: { text: `Файл больше ${env.uploadMaxMb} МБ`, hint: "Разделите документ или уменьшите разрешение скана" } })); return; }
      m.mutate(v);
    },
    pendingFor: m.isPending ? key(m.variables) : null,
    errorFor: (k: string) => errors[k],
    warningsFor: (k: string) => warnings[k],
    clearError: (k: string) => setErrors((s) => ({ ...s, [k]: undefined })),
  };
};

/** Удалить документ из заявки (до решения — проверяет сервер). */
export const useRemoveDocument = (applicationId: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (docId: string) => applicantApi.remove(applicationId, docId),
    onSettled: () => qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) }),
  });
};
