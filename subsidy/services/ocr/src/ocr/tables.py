"""Таблицы страницы (OCR-06, FR-32).

Текстовый слой PDF — `pdfplumber.find_tables` (таблицы с линейками). Если линеек нет или страница —
скан, строки и колонки собираются по bbox слов: слова группируются в строки по вертикали, ячейки
делятся по широким горизонтальным промежуткам, колонки выравниваются по самой «широкой» строке.
Таблица — не меньше MIN_ROWS подряд идущих строк с не меньше MIN_COLS ячейками.
"""

from __future__ import annotations

from statistics import median
from typing import Any

from ocr.models import Table, Word

MIN_ROWS = 3
MIN_COLS = 3
# промежуток между ячейками — больше GAP_K высот строки (между словами обычно ≈ 0.3 высоты)
GAP_K = 1.2
# слово в той же строке, если центр по вертикали ближе ROW_K высоты к центру строки
ROW_K = 0.5


def _height(words: list[Word]) -> float:
    return float(median(w.bbox[3] - w.bbox[1] for w in words)) if words else 0.0


def rows_by_bbox(words: list[Word]) -> list[list[Word]]:
    """Слова → строки по вертикальному центру (не по разметке tesseract: она режет таблицы на блоки)."""
    h = _height(words)
    rows: list[list[Word]] = []
    centers: list[float] = []
    for w in sorted(words, key=lambda x: ((x.bbox[1] + x.bbox[3]) / 2, x.bbox[0])):
        c = (w.bbox[1] + w.bbox[3]) / 2
        if rows and abs(c - centers[-1]) <= ROW_K * h:
            rows[-1].append(w)
            centers[-1] = sum((x.bbox[1] + x.bbox[3]) / 2 for x in rows[-1]) / len(rows[-1])
        else:
            rows.append([w])
            centers.append(c)
    return [sorted(r, key=lambda x: x.bbox[0]) for r in rows]


def cells(row: list[Word], h: float) -> list[tuple[str, int, int]]:
    """Строка → ячейки (текст, x0, x1): разрыв там, где промежуток между словами шире GAP_K × h."""
    out: list[tuple[str, int, int]] = []
    for w in row:
        if out and w.bbox[0] - out[-1][2] <= GAP_K * h:
            t, x0, _ = out[-1]
            out[-1] = (t + " " + w.t, x0, w.bbox[2])
        else:
            out.append((w.t, w.bbox[0], w.bbox[2]))
    return out


def _align(run: list[list[tuple[str, int, int]]]) -> list[list[str]]:
    anchors = max(run, key=len)
    table: list[list[str]] = []
    for row in run:
        out = [""] * len(anchors)
        for t, x0, x1 in row:
            mid = (x0 + x1) / 2
            idx = min(range(len(anchors)), key=lambda i: abs(mid - (anchors[i][1] + anchors[i][2]) / 2))
            out[idx] = (out[idx] + " " + t).strip()
        table.append(out)
    return table


def _bbox(rows: list[list[Word]]) -> list[int]:
    ws = [w for r in rows for w in r]
    return [
        min(w.bbox[0] for w in ws),
        min(w.bbox[1] for w in ws),
        max(w.bbox[2] for w in ws),
        max(w.bbox[3] for w in ws),
    ]


def from_words(words: list[Word]) -> list[Table]:
    """Таблицы по bbox слов (сканы и PDF без линеек)."""
    h = _height(words)
    if h <= 0:
        return []
    rows = rows_by_bbox(words)
    split = [cells(r, h) for r in rows]
    tables: list[Table] = []
    start = 0
    for i in range(len(rows) + 1):
        wide = i < len(rows) and len(split[i]) >= MIN_COLS
        if wide:
            continue
        if i - start >= MIN_ROWS:
            tables.append(Table(rows=_align(split[start:i]), bbox=_bbox(rows[start:i]), source="words"))
        start = i + 1
    return tables


def from_pdfplumber(page: Any, k: float) -> list[Table]:
    """Таблицы с линейками из текстового слоя; bbox в пикселях рендера (коэффициент k)."""
    out: list[Table] = []
    for t in page.find_tables():
        rows = [[(c or "").replace("\n", " ").strip() for c in r] for r in t.extract()]
        rows = [r for r in rows if any(r)]
        if len(rows) >= MIN_ROWS and max(len(r) for r in rows) >= MIN_COLS:
            x0, top, x1, bottom = t.bbox
            out.append(
                Table(rows=rows, bbox=[int(x0 * k), int(top * k), int(x1 * k), int(bottom * k)], source="text_layer")
            )
    return out
