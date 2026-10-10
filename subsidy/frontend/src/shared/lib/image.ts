const MAX_PIXELS = 4_000_000;
const QUALITY = 0.9;

/**
 * Сжатие снимка перед отправкой (RULES §Ограничения): больше 4 МП → уменьшаем до 4 МП, JPEG 0.9 — текст остаётся читаемым.
 * PDF, HEIC, TIFF и небольшие снимки уходят как есть. Пригодность всё равно решает сервер.
 */
export const shrinkImage = async (file: File): Promise<File> => {
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return file;
  let bmp: ImageBitmap;
  try {
    bmp = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    return file;
  }
  const px = bmp.width * bmp.height;
  if (px <= MAX_PIXELS) { bmp.close(); return file; }
  const k = Math.sqrt(MAX_PIXELS / px);
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bmp.width * k);
  canvas.height = Math.round(bmp.height * k);
  canvas.getContext("2d")?.drawImage(bmp, 0, 0, canvas.width, canvas.height);
  bmp.close();
  const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", QUALITY));
  if (!blob || blob.size >= file.size) return file;
  return new File([blob], file.name.replace(/\.\w+$/, "") + ".jpg", { type: "image/jpeg", lastModified: file.lastModified });
};
