"""FastAPI-приложение ocr (порт 8000, наружу не публикуется)."""

from __future__ import annotations

import io
import logging
from typing import Annotated, Any

from fastapi import Depends, FastAPI, File, Query, Request, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from ocr import pipeline
from ocr.auth import require_service
from ocr.config import settings
from ocr.errors import OcrError
from ocr.images import render_page
from ocr.metrics import REQUESTS
from ocr.models import ExtractResult

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(title="subsidy-ocr", version="1.0.0", description="Текст, слова с bbox, метрики качества, превью")

Service = Annotated[dict[str, Any], Depends(require_service)]


@app.exception_handler(OcrError)
async def _ocr_error(_: Request, e: OcrError) -> JSONResponse:
    return JSONResponse(
        status_code=e.status,
        media_type="application/problem+json",
        content={"type": "about:blank", "title": e.code, "status": e.status, "code": e.code, "detail": e.detail},
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/metrics", include_in_schema=False)
def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/v1/extract", response_model=ExtractResult)
async def extract(_: Service, file: Annotated[UploadFile, File()]) -> ExtractResult:
    data = await file.read()
    try:
        out = await run_in_threadpool(pipeline.extract, data, settings)
    except OcrError as e:
        REQUESTS.labels("extract", e.code).inc()
        raise
    REQUESTS.labels("extract", "ok").inc()
    return out


@app.post("/v1/render", responses={200: {"content": {"image/png": {}}}})
async def render(_: Service, file: Annotated[UploadFile, File()], page: Annotated[int, Query(ge=1)] = 1) -> Response:
    data = await file.read()
    mime = pipeline.detect(data, settings)

    def _png() -> bytes:
        img = render_page(data, mime, page - 1, settings.dpi, settings.max_pages, settings.max_side_px)
        buf = io.BytesIO()
        img.save(buf, format="PNG", optimize=False)
        return buf.getvalue()

    png = await run_in_threadpool(_png)
    REQUESTS.labels("render", "ok").inc()
    return Response(content=png, media_type="image/png")
