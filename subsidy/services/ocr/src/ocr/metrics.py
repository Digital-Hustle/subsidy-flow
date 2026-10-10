"""Метрики Prometheus."""

from __future__ import annotations

from prometheus_client import Counter, Histogram

PAGES = Counter("ocr_pages_total", "Обработано страниц", ["path"])
PAGE_SECONDS = Histogram("ocr_page_seconds", "Время обработки страницы", buckets=(0.5, 1, 2, 3, 5, 8, 13, 20, 30))
REQUESTS = Counter("ocr_requests_total", "Запросы", ["endpoint", "outcome"])
