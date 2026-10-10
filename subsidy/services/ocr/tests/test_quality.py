import cv2
import numpy as np
from conftest import document

from ocr.models import Word
from ocr.preprocess import to_gray
from ocr.quality import cut_off, measure


def test_blur_drops_on_blurred_image() -> None:
    sharp = to_gray(document(["ПАСПОРТ ГРАЖДАНИНА", "Фамилия ИВАНОВ"]))
    blurred = cv2.GaussianBlur(sharp, (21, 21), 8)
    assert measure(sharp, [], 300).blur > 5 * measure(blurred, [], 300).blur


def test_dark_image_has_low_brightness() -> None:
    g = to_gray(document(["ТЕКСТ"]))
    dark = (g.astype(np.float32) * 0.15).astype(np.uint8)
    assert measure(dark, [], 0).brightness < 0.2
    assert measure(g, [], 0).brightness > 0.8


def test_glare_fraction() -> None:
    g = np.full((500, 500), 120, dtype=np.uint8)
    g[100:300, 100:300] = 255
    q = measure(g, [], 0)
    assert 0.1 < q.glare < 0.2


def test_cut_off_when_words_touch_edge() -> None:
    inside = [Word(t="ТЕКСТ", bbox=[100, 100, 200, 130], conf=0.9) for _ in range(10)]
    edge = [Word(t="ТЕКСТ", bbox=[0, 100 + i * 40, 120, 130 + i * 40], conf=0.9) for i in range(5)]
    assert not cut_off(inside, 1000, 1000)
    assert cut_off(inside + edge, 1000, 1000)


def test_white_scan_has_no_glare() -> None:
    assert measure(to_gray(document(["СКАН НА БЕЛОМ ФОНЕ"])), [], 300).glare == 0.0
