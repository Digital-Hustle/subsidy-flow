"""Ошибки сервиса → problem+json."""

from __future__ import annotations


class OcrError(Exception):
    def __init__(self, code: str, status: int, detail: str) -> None:
        super().__init__(detail)
        self.code = code
        self.status = status
        self.detail = detail


def corrupted() -> OcrError:
    return OcrError("FILE_CORRUPTED", 422, "Файл повреждён или не открывается")


def too_many_pages(limit: int) -> OcrError:
    return OcrError("TOO_MANY_PAGES", 422, f"Слишком много страниц (максимум {limit})")


def unsupported() -> OcrError:
    return OcrError("FILE_TYPE_UNSUPPORTED", 415, "Формат не поддерживается")
