"""Определение формата по сигнатуре (не по имени файла)."""

from __future__ import annotations

PDF = "application/pdf"
JPEG = "image/jpeg"
PNG = "image/png"
TIFF = "image/tiff"
HEIC = "image/heic"
WEBP = "image/webp"


def sniff(data: bytes) -> str | None:
    head = data[:16]
    if head.startswith(b"%PDF"):
        return PDF
    if head.startswith(b"\xff\xd8\xff"):
        return JPEG
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        return PNG
    if head[:4] in (b"II*\x00", b"MM\x00*"):
        return TIFF
    if head[4:8] == b"ftyp" and head[8:12] in (b"heic", b"heix", b"mif1", b"msf1", b"heim", b"heis", b"hevc"):
        return HEIC
    if head.startswith(b"RIFF") and head[8:12] == b"WEBP":
        return WEBP
    return None
