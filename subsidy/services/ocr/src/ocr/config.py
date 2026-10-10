"""Настройки из окружения (пороги — только измерения, решения принимает docproc)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _int(name: str, default: int) -> int:
    return int(os.environ.get(name, str(default)))


def _float(name: str, default: float) -> float:
    return float(os.environ.get(name, str(default)))


@dataclass(frozen=True)
class Settings:
    langs: str = field(default_factory=lambda: os.environ.get("OCR_LANGS", "rus+eng"))
    dpi: int = field(default_factory=lambda: _int("OCR_DPI", 300))
    max_pages: int = field(default_factory=lambda: _int("OCR_MAX_PAGES", 60))
    max_mb: int = field(default_factory=lambda: _int("OCR_MAX_MB", 30))
    page_timeout_s: float = field(default_factory=lambda: _float("OCR_PAGE_TIMEOUT", 20.0))
    max_side_px: int = field(default_factory=lambda: _int("OCR_MAX_SIDE_PX", 3500))
    text_layer_min_chars: int = field(default_factory=lambda: _int("OCR_TEXT_LAYER_MIN_CHARS", 20))
    oidc_issuer: str = field(default_factory=lambda: os.environ.get("OIDC_ISSUER_URI", ""))
    jwks_uri: str = field(default_factory=lambda: os.environ.get("OIDC_JWK_SET_URI", ""))
    audience: str = field(default_factory=lambda: os.environ.get("OCR_AUDIENCE", "ocr"))
    allowed_clients: tuple[str, ...] = field(
        default_factory=lambda: tuple(os.environ.get("OCR_ALLOWED_CLIENTS", "svc-docproc,svc-package").split(","))
    )


settings = Settings()
