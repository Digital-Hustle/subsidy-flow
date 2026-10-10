"""Tesseract 5 через pytesseract: слова + bbox + уверенность; подбор ориентации 0/90/180/270."""

from __future__ import annotations

from dataclasses import dataclass

import cv2
import pytesseract

from ocr.models import Word
from ocr.preprocess import Gray, u8

_ROT = {
    90: cv2.ROTATE_90_CLOCKWISE,
    180: cv2.ROTATE_180,
    270: cv2.ROTATE_90_COUNTERCLOCKWISE,
}


@dataclass
class OcrPage:
    words: list[Word]
    text: str
    mean_conf: float
    rotation: int
    image: Gray


def rotate90(gray: Gray, deg: int) -> Gray:
    if deg == 0:
        return gray
    out: Gray = u8(cv2.rotate(gray, _ROT[deg]))
    return out


def recognize(gray: Gray, langs: str, timeout: float, psm: int = 3, whitelist: str | None = None) -> OcrPage:
    config = f"--oem 1 --psm {psm}"
    if whitelist:
        config += f" -c tessedit_char_whitelist={whitelist}"
    data = pytesseract.image_to_data(
        gray, lang=langs, config=config, output_type=pytesseract.Output.DICT, timeout=timeout
    )
    words: list[Word] = []
    lines: dict[tuple[int, int, int], int] = {}
    text_lines: list[list[str]] = []
    for i, raw in enumerate(data["text"]):
        t = str(raw).strip()
        conf = float(data["conf"][i])
        if not t or conf < 0:
            continue
        key = (int(data["block_num"][i]), int(data["par_num"][i]), int(data["line_num"][i]))
        if key not in lines:
            lines[key] = len(lines)
            text_lines.append([])
        ln = lines[key]
        text_lines[ln].append(t)
        x, y, w, h = int(data["left"][i]), int(data["top"][i]), int(data["width"][i]), int(data["height"][i])
        words.append(Word(t=t, bbox=[x, y, x + w, y + h], conf=round(conf / 100.0, 3), line=ln))
    mean = sum(w.conf for w in words) / len(words) if words else 0.0
    return OcrPage(words, "\n".join(" ".join(x) for x in text_lines), round(mean, 3), 0, gray)


def _score(p: OcrPage) -> float:
    good = [w for w in p.words if w.conf >= 0.6 and len(w.t) >= 3]
    return sum(len(w.t) * w.conf for w in good)


def _small(gray: Gray) -> Gray:
    h, w = gray.shape
    k = min(1.0, 1200 / max(h, w))
    if k >= 1.0:
        return gray
    out: Gray = u8(cv2.resize(gray, (int(w * k), int(h * k)), interpolation=cv2.INTER_AREA))
    return out


def best_orientation(gray: Gray, langs: str, timeout: float) -> OcrPage:
    """Распознать; при слабом результате перебрать повороты на уменьшенной копии и взять лучший."""
    first = recognize(gray, langs, timeout)
    if first.mean_conf >= 0.7 and len(first.words) >= 8:
        return first
    small = _small(gray)
    scores = {0: _score(recognize(small, langs, timeout, psm=3))}
    for deg in (90, 180, 270):
        scores[deg] = _score(recognize(rotate90(small, deg), langs, timeout, psm=3))
    deg = max(scores, key=lambda d: scores[d])
    if deg == 0 or scores[deg] <= scores[0] * 1.3:
        return first
    rotated = rotate90(gray, deg)
    page = recognize(rotated, langs, timeout)
    page.rotation = deg
    page.image = rotated
    return page
