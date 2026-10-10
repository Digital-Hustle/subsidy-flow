"""Синтетические документы для тестов (rules.md §1: без реальных ПДн)."""

from __future__ import annotations

import io

from PIL import Image, ImageDraw, ImageFont

SANS = "/usr/share/fonts/liberation-sans/LiberationSans-Regular.ttf"
MONO = "/usr/share/fonts/liberation-mono/LiberationMono-Regular.ttf"
ALT = {
    SANS: "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    MONO: "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
}


def font(size: int, mono: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    p = MONO if mono else SANS
    for path in (p, ALT[p]):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def document(lines: list[str], size: tuple[int, int] = (1700, 1100), mono: bool = False, px: int = 44) -> Image.Image:
    img = Image.new("RGB", size, "white")
    d = ImageDraw.Draw(img)
    f = font(px, mono)
    y = 90
    for line in lines:
        d.text((90, y), line, fill="black", font=f)
        y += int(px * 1.7)
    return img


def png(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def jpeg(img: Image.Image, exif_orientation: int | None = None) -> bytes:
    buf = io.BytesIO()
    if exif_orientation is not None:
        exif = Image.Exif()
        exif[0x0112] = exif_orientation
        img.save(buf, format="JPEG", quality=92, exif=exif)
    else:
        img.save(buf, format="JPEG", quality=92)
    return buf.getvalue()


def text_pdf(lines: list[str]) -> bytes:
    """PDF с текстовым слоем (pypdfium2 умеет создавать страницы с текстом)."""
    import pypdfium2 as pdfium
    import pypdfium2.raw as raw

    doc = pdfium.PdfDocument.new()
    page = doc.new_page(595, 842)
    fnt = raw.FPDFText_LoadStandardFont(doc.raw, b"Helvetica")
    y = 780.0
    for line in lines:
        obj = raw.FPDFPageObj_CreateTextObj(doc.raw, fnt, 14.0)
        ws = (line + "\x00").encode("utf-16-le")
        buf = (raw.FPDF_WCHAR * (len(ws) // 2)).from_buffer_copy(ws)
        raw.FPDFText_SetText(obj, buf)
        raw.FPDFPageObj_Transform(obj, 1, 0, 0, 1, 60, y)
        raw.FPDFPage_InsertObject(page.raw, obj)
        y -= 24
    raw.FPDFPage_GenerateContent(page.raw)
    out = io.BytesIO()
    doc.save(out)
    return out.getvalue()


def image_pdf(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="PDF", resolution=200.0)
    return buf.getvalue()
