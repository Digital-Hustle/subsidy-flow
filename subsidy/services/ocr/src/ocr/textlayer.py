"""Текстовый слой PDF (pdfplumber): слова с bbox в пикселях страницы при рендере OCR_DPI."""

from __future__ import annotations

import io
from dataclasses import dataclass

import pdfplumber

from ocr import errors
from ocr.images import pdf_scale
from ocr.models import Table, Word
from ocr.tables import from_pdfplumber, from_words


@dataclass
class TextPage:
    width: int
    height: int
    words: list[Word]
    text: str
    tables: list[Table]


def read_text_layer(data: bytes, dpi: int, max_side: int) -> list[TextPage]:
    out: list[TextPage] = []
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            for page in pdf.pages:
                w_pt, h_pt = float(page.width), float(page.height)
                k = pdf_scale(w_pt, h_pt, dpi, max_side)
                words: list[Word] = []
                line = -1
                last_top: float | None = None
                for w in page.extract_words(keep_blank_chars=False, use_text_flow=True):
                    top = float(w["top"])
                    if last_top is None or abs(top - last_top) > 3:
                        line += 1
                        last_top = top
                    words.append(
                        Word(
                            t=str(w["text"]),
                            bbox=[
                                int(float(w["x0"]) * k),
                                int(top * k),
                                int(float(w["x1"]) * k),
                                int(float(w["bottom"]) * k),
                            ],
                            conf=1.0,
                            line=line,
                        )
                    )
                text = page.extract_text() or ""
                tables = from_pdfplumber(page, k) or from_words(words)
                out.append(TextPage(int(w_pt * k), int(h_pt * k), words, text, tables))
    except Exception as e:  # pdfminer бросает разные исключения на битых файлах
        raise errors.corrupted() from e
    return out
