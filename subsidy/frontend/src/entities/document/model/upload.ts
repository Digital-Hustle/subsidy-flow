export type DocumentRejection = { name: string; code: string; detail: string; hint?: string | null };

export const DOCUMENT_ACCEPT = ".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png";

const DOCUMENT_MIMES = new Set([
  "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "image/jpeg", "image/png",
]);

/** Только удобство выбора. Содержимое документа и ограничения проверяет сервер. */
export const splitDocumentFiles = (files: File[]) => {
  const accepted: File[] = [];
  const rejected: DocumentRejection[] = [];
  for (const file of files) {
    if (/\.(pdf|docx?|xlsx?|jpe?g|png)$/i.test(file.name) || DOCUMENT_MIMES.has(file.type)) accepted.push(file);
    else rejected.push({ name: file.name, code: "FILE_TYPE_UNSUPPORTED", detail: "Этот формат не поддерживается", hint: "Выберите PDF, DOC/DOCX, XLS/XLSX или JPG/PNG" });
  }
  return { accepted, rejected };
};
