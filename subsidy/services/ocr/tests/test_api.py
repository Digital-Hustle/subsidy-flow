from typing import Any

from conftest import document, png
from fastapi.testclient import TestClient

from ocr.auth import require_service
from ocr.main import app


def _service() -> dict[str, Any]:
    return {"azp": "svc-docproc"}


def test_health_is_public() -> None:
    assert TestClient(app).get("/health").json() == {"status": "UP"}


def test_extract_requires_token() -> None:
    r = TestClient(app).post("/v1/extract", files={"file": ("a.png", b"x", "image/png")})
    assert r.status_code == 401
    assert r.json()["code"] == "UNAUTHORIZED"


def test_extract_and_render_with_service_token() -> None:
    app.dependency_overrides[require_service] = _service
    try:
        c = TestClient(app)
        data = png(document(["СЧЁТ НА ОПЛАТУ ИНН"]))
        r = c.post("/v1/extract", files={"file": ("a.png", data, "image/png")})
        assert r.status_code == 200
        body = r.json()
        assert body["pages"][0]["width"] == 1700
        img = c.post("/v1/render?page=1", files={"file": ("a.png", data, "image/png")})
        assert img.status_code == 200
        assert img.headers["content-type"] == "image/png"
        miss = c.post("/v1/render?page=3", files={"file": ("a.png", data, "image/png")})
        assert miss.status_code == 404
    finally:
        app.dependency_overrides.clear()
