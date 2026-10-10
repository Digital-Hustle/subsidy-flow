import io

import pypdfium2 as pdfium
import pypdfium2.raw as raw
from conftest import font, png
from PIL import Image, ImageDraw

from ocr import pipeline
from ocr.config import Settings
from ocr.models import Word
from ocr.tables import from_words, rows_by_bbox

S = Settings()
ROWS = [
    ["Наименование", "Кол-во", "Цена", "Сумма"],
    ["Станок", "2", "150000.00", "300000.00"],
    ["Монтаж", "1", "45000.00", "45000.00"],
    ["Доставка", "1", "5000.00", "5000.00"],
]
COLS = [80, 700, 950, 1250]


def words(rows: list[list[str]], y0: int = 300, h: int = 30) -> list[Word]:
    out: list[Word] = []
    for r, row in enumerate(rows):
        y = y0 + r * 60 + (r % 2) * 4  # строки немного «пляшут», как на скане
        for c, cell in enumerate(row):
            x = COLS[c]
            for part in cell.split():
                out.append(Word(t=part, bbox=[x, y, x + 18 * len(part), y + h], conf=0.9))
                x += 18 * len(part) + 10
    return out


def test_rows_grouped_by_vertical_center_not_tesseract_lines() -> None:
    ws = words(ROWS)
    for i, w in enumerate(ws):
        w.line = i  # tesseract разнёс ячейки по разным блокам
    rows = rows_by_bbox(ws)
    assert len(rows) == 4
    assert [w.t for w in rows[1]] == ["Станок", "2", "150000.00", "300000.00"]


def test_table_from_words_columns_aligned() -> None:
    para = [
        Word(t=t, bbox=[80 + i * 120, 100, 180 + i * 120, 130], conf=0.9)
        for i, t in enumerate(["Смета", "затрат", "на", "приобретение", "оборудования"])
    ]
    tail = [
        Word(t="Итого", bbox=[80, 600, 200, 630], conf=0.9),
        Word(t="350000.00", bbox=[1250, 600, 1430, 630], conf=0.9),
    ]
    tables = from_words(para + words(ROWS) + tail)
    assert len(tables) == 1
    t = tables[0]
    assert t.source == "words"
    assert t.rows[0] == ["Наименование", "Кол-во", "Цена", "Сумма"]
    assert t.rows[2] == ["Монтаж", "1", "45000.00", "45000.00"]
    assert t.bbox[0] == 80 and t.bbox[1] >= 300


def test_short_row_aligned_to_its_column() -> None:
    rows = [*ROWS, ["Скидка", "", "", "-1000.00"]]
    t = from_words(words(rows))
    # строка из двух ячеек не продолжает таблицу (меньше MIN_COLS), таблица — 4 строки
    assert len(t) == 1 and len(t[0].rows) == 4


def test_two_rows_is_not_a_table() -> None:
    assert from_words(words(ROWS[:2])) == []


def _table_pdf() -> bytes:
    doc = pdfium.PdfDocument.new()
    page = doc.new_page(595, 842)
    fnt = raw.FPDFText_LoadStandardFont(doc.raw, b"Helvetica")
    xs = [40, 250, 330, 450]
    data = [
        ["Item", "Qty", "Price", "Amount"],
        ["Lathe", "2", "150000.00", "300000.00"],
        ["Mounting", "1", "45000.00", "45000.00"],
        ["Delivery", "1", "5000.00", "5000.00"],
    ]
    for r, row in enumerate(data):
        for c, cell in enumerate(row):
            obj = raw.FPDFPageObj_CreateTextObj(doc.raw, fnt, 11.0)
            ws = (cell + "\x00").encode("utf-16-le")
            raw.FPDFText_SetText(obj, (raw.FPDF_WCHAR * (len(ws) // 2)).from_buffer_copy(ws))
            raw.FPDFPageObj_Transform(obj, 1, 0, 0, 1, xs[c], 700 - r * 22)
            raw.FPDFPage_InsertObject(page.raw, obj)
    raw.FPDFPage_GenerateContent(page.raw)
    out = io.BytesIO()
    doc.save(out)
    return out.getvalue()


def test_text_layer_table_without_rulings() -> None:
    page = pipeline.extract(_table_pdf(), S).pages[0]
    assert page.textLayer
    assert len(page.tables) == 1
    assert page.tables[0].rows[1] == ["Lathe", "2", "150000.00", "300000.00"]


def test_scanned_table_by_ocr_bbox() -> None:
    img = Image.new("RGB", (1700, 1100), "white")
    d = ImageDraw.Draw(img)
    f = font(36)
    for r, row in enumerate(ROWS):
        for c, cell in enumerate(row):
            d.text((COLS[c], 200 + r * 80), cell, fill="black", font=f)
    page = pipeline.extract(png(img), S).pages[0]
    assert len(page.tables) == 1
    rows = page.tables[0].rows
    assert len(rows) == 4 and len(rows[0]) == 4
    assert rows[1][3].replace(" ", "") == "300000.00"
