"""Загрузка страниц: изображения (EXIF-поворот, многостраничный TIFF, HEIC) и PDF (pypdfium2)."""

from __future__ import annotations

import io
from collections.abc import Iterator

import pillow_heif
import pypdfium2 as pdfium
from PIL import Image, ImageOps, UnidentifiedImageError

from ocr import errors
from ocr.formats import PDF

pillow_heif.register_heif_opener()
Image.MAX_IMAGE_PIXELS = 120_000_000


def _limit(img: Image.Image, max_side: int) -> Image.Image:
    w, h = img.size
    side = max(w, h)
    if side <= max_side:
        return img
    k = max_side / side
    return img.resize((max(1, int(w * k)), max(1, int(h * k))), Image.Resampling.LANCZOS)


def image_dpi(img: Image.Image) -> int:
    raw = img.info.get("dpi")
    if isinstance(raw, tuple) and raw and isinstance(raw[0], int | float) and raw[0] > 1:
        return round(float(raw[0]))
    return 0


def raster_pages(data: bytes, max_pages: int, max_side: int) -> Iterator[tuple[Image.Image, int]]:
    """Страницы изображения: (RGB-картинка после EXIF-поворота, dpi из метаданных или 0)."""
    try:
        img = Image.open(io.BytesIO(data))
        n = getattr(img, "n_frames", 1)
        if n > max_pages:
            raise errors.too_many_pages(max_pages)
        for i in range(n):
            img.seek(i)
            dpi = image_dpi(img)
            frame = ImageOps.exif_transpose(img.copy()) or img.copy()
            yield _limit(frame.convert("RGB"), max_side), dpi
    except errors.OcrError:
        raise
    except (UnidentifiedImageError, OSError, ValueError, SyntaxError) as e:
        raise errors.corrupted() from e


def pdf_page_count(data: bytes) -> int:
    try:
        doc = pdfium.PdfDocument(data)
    except pdfium.PdfiumError as e:
        raise errors.corrupted() from e
    try:
        return len(doc)
    finally:
        doc.close()


def pdf_scale(width_pt: float, height_pt: float, dpi: int, max_side: int) -> float:
    """Пикселей на пункт PDF: dpi/72, но не больше max_side по длинной стороне."""
    k = dpi / 72
    side = max(width_pt, height_pt) * k
    if side > max_side:
        k = k * max_side / side
    return k


def render_pdf_page(data: bytes, index: int, dpi: int, max_side: int) -> Image.Image:
    try:
        doc = pdfium.PdfDocument(data)
    except pdfium.PdfiumError as e:
        raise errors.corrupted() from e
    try:
        page = doc[index]
        w, h = page.get_size()
        img: Image.Image = page.render(scale=pdf_scale(w, h, dpi, max_side)).to_pil().convert("RGB")
        page.close()
        return img
    finally:
        doc.close()


def render_page(data: bytes, mime: str, index: int, dpi: int, max_pages: int, max_side: int) -> Image.Image:
    """Картинка страницы в тех же координатах, что и bbox в /v1/extract."""
    if mime == PDF:
        if index >= pdf_page_count(data):
            raise errors.OcrError("PAGE_NOT_FOUND", 404, "Нет такой страницы")
        return render_pdf_page(data, index, dpi, max_side)
    for i, (img, _) in enumerate(raster_pages(data, max_pages, max_side)):
        if i == index:
            return img
    raise errors.OcrError("PAGE_NOT_FOUND", 404, "Нет такой страницы")
