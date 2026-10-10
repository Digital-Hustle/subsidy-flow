"""Конвейер /v1/extract: формат → страницы → текстовый слой или OCR → качество."""

from __future__ import annotations

import logging
import time

import pytesseract
from PIL import Image

from ocr import errors
from ocr.config import Settings
from ocr.formats import HEIC, JPEG, PDF, PNG, TIFF, WEBP, sniff
from ocr.images import pdf_page_count, raster_pages, render_pdf_page
from ocr.metrics import PAGE_SECONDS, PAGES
from ocr.models import ExtractResult, Page
from ocr.preprocess import prepare, to_gray
from ocr.quality import measure
from ocr.tables import from_words
from ocr.tesseract import best_orientation
from ocr.textlayer import read_text_layer

log = logging.getLogger("ocr")

_RASTER = {JPEG, PNG, TIFF, HEIC, WEBP}


def detect(data: bytes, s: Settings) -> str:
    if not data:
        raise errors.OcrError("FILE_EMPTY", 400, "Файл пустой")
    if len(data) > s.max_mb * 1024 * 1024:
        raise errors.OcrError("FILE_TOO_LARGE", 413, f"Файл больше {s.max_mb} МБ")
    mime = sniff(data)
    if mime is None or (mime != PDF and mime not in _RASTER):
        raise errors.unsupported()
    return mime


def _ocr_page(n: int, img: Image.Image, dpi: int, s: Settings) -> Page:
    started = time.monotonic()
    try:
        gray, _ = prepare(img)
        res = best_orientation(gray, s.langs, s.page_timeout_s)
        h, w = res.image.shape
        q = measure(to_gray(img) if res.rotation == 0 else res.image, res.words, dpi)
        page = Page(
            n=n,
            width=w,
            height=h,
            textLayer=False,
            text=res.text,
            words=res.words,
            tables=from_words(res.words),
            quality=q,
            meanConf=res.mean_conf,
            rotation=res.rotation,
        )
        PAGES.labels("ocr").inc()
        return page
    except RuntimeError as e:
        code = "TIMEOUT" if "timeout" in str(e).lower() else "OCR_FAILED"
        log.warning("page %s failed: %s", n, code)
        return Page(n=n, width=img.width, height=img.height, textLayer=False, text="", words=[], error=code)
    except pytesseract.TesseractError:
        log.warning("page %s failed: OCR_FAILED", n)
        return Page(n=n, width=img.width, height=img.height, textLayer=False, text="", words=[], error="OCR_FAILED")
    finally:
        PAGE_SECONDS.observe(time.monotonic() - started)


def extract(data: bytes, s: Settings) -> ExtractResult:
    started = time.monotonic()
    mime = detect(data, s)
    pages: list[Page] = []
    if mime == PDF:
        count = pdf_page_count(data)
        if count == 0:
            raise errors.corrupted()
        if count > s.max_pages:
            raise errors.too_many_pages(s.max_pages)
        layer = read_text_layer(data, s.dpi, s.max_side_px)
        for i in range(count):
            tp = layer[i] if i < len(layer) else None
            if tp is not None and len(tp.text.strip()) >= s.text_layer_min_chars:
                pages.append(
                    Page(
                        n=i + 1,
                        width=tp.width,
                        height=tp.height,
                        textLayer=True,
                        text=tp.text,
                        words=tp.words,
                        tables=tp.tables,
                        meanConf=1.0,
                    )
                )
                PAGES.labels("text_layer").inc()
                continue
            pages.append(_ocr_page(i + 1, render_pdf_page(data, i, s.dpi, s.max_side_px), s.dpi, s))
    else:
        for i, (img, dpi) in enumerate(raster_pages(data, s.max_pages, s.max_side_px)):
            pages.append(_ocr_page(i + 1, img, dpi, s))
    took = int((time.monotonic() - started) * 1000)
    log.info("extract mime=%s pages=%d words=%d ms=%d", mime, len(pages), sum(len(p.words) for p in pages), took)
    return ExtractResult(mime=mime, pages=pages, durationMs=took)
