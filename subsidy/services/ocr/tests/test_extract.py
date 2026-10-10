import pytest
from conftest import document, image_pdf, jpeg, png, text_pdf
from PIL import Image

from ocr import pipeline
from ocr.config import Settings
from ocr.errors import OcrError

S = Settings()


def joined(res: object) -> str:
    return " ".join(w.t for p in res.pages for w in p.words).upper()  # type: ignore[attr-defined]


def test_png_ocr_words_with_bbox() -> None:
    img = document(["СЧЁТ НА ОПЛАТУ", "НОМЕР 17 ОТ 01.03.2026", "ПОСТАВЩИК ЛУЧ"])
    res = pipeline.extract(png(img), S)
    assert res.mime == "image/png"
    page = res.pages[0]
    assert not page.textLayer
    text = joined(res)
    assert "ОПЛАТУ" in text
    assert "ЛУЧ" in text
    w = next(w for w in page.words if "ЛУЧ" in w.t.upper())
    assert 0 <= w.bbox[0] < w.bbox[2] <= page.width
    assert 0 <= w.bbox[1] < w.bbox[3] <= page.height
    assert w.conf > 0.5
    assert page.quality is not None and page.quality.blur > 0


def test_text_layer_pdf_is_read_without_ocr() -> None:
    res = pipeline.extract(text_pdf(["Tax certificate", "INN 7707083893", "Issued 2026-03-01"]), S)
    page = res.pages[0]
    assert page.textLayer
    assert "7707083893" in page.text
    w = next(w for w in page.words if w.t == "7707083893")
    assert w.bbox[2] > w.bbox[0]
    assert abs(page.width / page.height - 595 / 842) < 0.01
    assert max(page.width, page.height) <= S.max_side_px


def test_scanned_pdf_falls_back_to_ocr() -> None:
    res = pipeline.extract(image_pdf(document(["СМЕТА РАСХОДОВ", "ИТОГО 4519"])), S)
    assert not res.pages[0].textLayer
    assert "СМЕТА" in joined(res)


def test_exif_rotation_is_applied() -> None:
    img = document(["ВЫПИСКА ИЗ РЕЕСТРА", "ОРГАНИЗАЦИЯ ВОСТОК"])
    rotated = img.transpose(Image.Transpose.ROTATE_90)
    res = pipeline.extract(jpeg(rotated, exif_orientation=8), S)
    assert "РЕЕСТРА" in joined(res)
    assert res.pages[0].width > res.pages[0].height


def test_upside_down_photo_is_recognized() -> None:
    img = document(["ДОГОВОР ПОСТАВКИ", "ПОКУПАТЕЛЬ ВОСТОК", "ПРОДАВЦА ЛУЧ"])
    res = pipeline.extract(png(img.rotate(180)), S)
    assert res.pages[0].rotation == 180
    assert "ДОГОВОР" in joined(res)


@pytest.mark.parametrize(
    ("data", "code"),
    [
        (b"", "FILE_EMPTY"),
        (b"hello world, not a document", "FILE_TYPE_UNSUPPORTED"),
        (b"%PDF-1.7 broken", "FILE_CORRUPTED"),
    ],
)
def test_errors(data: bytes, code: str) -> None:
    with pytest.raises(OcrError) as e:
        pipeline.extract(data, S)
    assert e.value.code == code
