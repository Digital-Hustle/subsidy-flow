"""Метрики качества снимка (ocr только измеряет; пороги и решения — в docproc)."""

from __future__ import annotations

import cv2
import numpy as np

from ocr.models import Quality, Word
from ocr.preprocess import Gray, u8

_EDGE = 0.008


def measure(gray: Gray, words: list[Word], dpi: int) -> Quality:
    h, w = gray.shape
    small = gray
    if max(h, w) > 1600:
        k = 1600 / max(h, w)
        small = u8(cv2.resize(gray, (int(w * k), int(h * k)), interpolation=cv2.INTER_AREA))
    blur = float(cv2.Laplacian(small, cv2.CV_64F).var())
    brightness = float(np.mean(small)) / 255.0
    glare = glare_fraction(small)
    return Quality(
        blur=round(blur, 2),
        brightness=round(brightness, 4),
        glare=round(glare, 4),
        cutOff=cut_off(words, w, h),
        dpi=dpi,
    )


def glare_fraction(gray: Gray) -> float:
    """Доля пересвета на фото. Скан с белым фоном (медиана ≥ 235) бликов не имеет — фон и так белый."""
    if float(np.median(gray)) >= 235:
        return 0.0
    smooth = cv2.GaussianBlur(gray, (5, 5), 0)
    return float(np.count_nonzero(smooth >= 250)) / float(smooth.size)


def cut_off(words: list[Word], w: int, h: int) -> bool:
    """Слова упираются в край кадра — документ, вероятно, обрезан."""
    if not words:
        return False
    mx, my = max(2, int(w * _EDGE)), max(2, int(h * _EDGE))
    touching = sum(
        1
        for wd in words
        if len(wd.t) >= 2 and (wd.bbox[0] <= mx or wd.bbox[1] <= my or wd.bbox[2] >= w - mx or wd.bbox[3] >= h - my)
    )
    return touching >= max(2, len(words) // 20)
