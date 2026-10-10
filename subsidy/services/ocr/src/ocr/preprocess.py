"""Предобработка снимка для Tesseract: оттенки серого, выравнивание наклона, CLAHE."""

from __future__ import annotations

from typing import Any

import cv2
import numpy as np
from numpy.typing import NDArray
from PIL import Image

Gray = NDArray[np.uint8]


def u8(x: Any) -> Gray:
    """Привести результат OpenCV к uint8-массиву (для строгой типизации)."""
    return np.asarray(x, dtype=np.uint8)


def to_gray(img: Image.Image) -> Gray:
    arr = np.asarray(img.convert("L"), dtype=np.uint8)
    return arr


def skew_angle(gray: Gray) -> float:
    """Угол наклона строк текста в градусах (−15..15), 0 если не определить."""
    small = gray
    h, w = gray.shape
    if max(h, w) > 1500:
        k = 1500 / max(h, w)
        small = u8(cv2.resize(gray, (int(w * k), int(h * k)), interpolation=cv2.INTER_AREA))
    inv = cv2.threshold(small, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)[1]
    inv = cv2.dilate(inv, cv2.getStructuringElement(cv2.MORPH_RECT, (25, 3)))
    lines = cv2.HoughLinesP(inv, 1, np.pi / 720, threshold=100, minLineLength=small.shape[1] // 4, maxLineGap=20)
    if lines is None:
        return 0.0
    angles: list[float] = []
    for x1, y1, x2, y2 in np.asarray(lines).reshape(-1, 4):
        a = float(np.degrees(np.arctan2(float(y2 - y1), float(x2 - x1))))
        if abs(a) <= 15:
            angles.append(a)
    if not angles:
        return 0.0
    return float(np.median(angles))


def rotate(gray: Gray, angle: float) -> Gray:
    if abs(angle) < 0.3:
        return gray
    h, w = gray.shape
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    out: Gray = u8(cv2.warpAffine(gray, m, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE))
    return out


def enhance(gray: Gray) -> Gray:
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    out: Gray = u8(clahe.apply(gray))
    return out


def prepare(img: Image.Image) -> tuple[Gray, float]:
    """Серое, выровненное, с контрастом; возвращает и угол поворота для отладки."""
    gray = to_gray(img)
    angle = skew_angle(gray)
    return enhance(rotate(gray, angle)), angle
