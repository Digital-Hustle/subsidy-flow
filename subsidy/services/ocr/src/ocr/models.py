"""Модели ответа /v1/extract (docs/API.md «ocr»)."""

from __future__ import annotations

from pydantic import BaseModel, Field


class Word(BaseModel):
    t: str
    bbox: list[int] = Field(description="[x0, y0, x1, y1] в пикселях страницы (width × height)")
    conf: float = Field(description="0..1")
    line: int = 0


class Quality(BaseModel):
    blur: float = Field(description="Дисперсия лапласиана; меньше — размытее")
    brightness: float = Field(description="Средняя яркость 0..1")
    glare: float = Field(description="Доля пересвеченных пикселей 0..1")
    cutOff: bool = Field(description="Текст упирается в край кадра")  # noqa: N815
    dpi: int


class Table(BaseModel):
    rows: list[list[str]] = Field(description="Строки × колонки, пустая ячейка — пустая строка")
    bbox: list[int] = Field(description="[x0, y0, x1, y1] в пикселях страницы")
    source: str = Field(description="text_layer (линейки pdfplumber) | words (по bbox слов)")


class Page(BaseModel):
    n: int
    width: int
    height: int
    textLayer: bool  # noqa: N815
    text: str
    words: list[Word]
    tables: list[Table] = Field(default_factory=list)
    quality: Quality | None = None
    meanConf: float = 0.0  # noqa: N815
    rotation: int = 0
    error: str | None = None


class ExtractResult(BaseModel):
    mime: str
    pages: list[Page]
    durationMs: int  # noqa: N815
