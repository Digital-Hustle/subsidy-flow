"""Прогон качества через публичный API стенда (docs/EVAL.md): вход как специалист → пакет → файлы → протокол.

uv run python run.py --api https://host --dir /tmp/pkg [--user specialist] [--report out.md]
Пароль — EVAL_PASSWORD. Сертификат стенда самоподписанный — проверка TLS выключена (включить: --verify).
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import html
import http.cookiejar
import json
import os
import re
import secrets
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

TERMINAL = {"PARSED", "NEEDS_REVIEW", "UNREADABLE", "FAILED", "REJECTED"}
CLIENT = "subsidy-specialist"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args: Any, **kwargs: Any) -> None:
        return None


class Api:
    def __init__(self, base: str, verify: bool) -> None:
        self.base = base.rstrip("/")
        self.ctx = ssl.create_default_context()
        if not verify:
            self.ctx.check_hostname = False
            self.ctx.verify_mode = ssl.CERT_NONE
        self.jar = http.cookiejar.CookieJar()
        self.token = ""

    def _opener(self, follow: bool = True) -> urllib.request.OpenerDirector:
        handlers: list[Any] = [
            urllib.request.HTTPSHandler(context=self.ctx),
            urllib.request.HTTPCookieProcessor(self.jar),
        ]
        if not follow:
            handlers.append(NoRedirect())
        return urllib.request.build_opener(*handlers)

    def login(self, user: str, password: str) -> None:
        """Authorization Code + PKCE, как браузер: форма Keycloak → code → token."""
        realm = f"{self.base}/auth/realms/subsidy/protocol/openid-connect"
        redirect = f"{self.base}/specialist/callback"
        verifier = secrets.token_urlsafe(48)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
        query = urllib.parse.urlencode(
            {
                "client_id": CLIENT,
                "response_type": "code",
                "scope": "openid",
                "redirect_uri": redirect,
                "code_challenge": challenge,
                "code_challenge_method": "S256",
                "state": secrets.token_hex(8),
            }
        )
        page = self._opener().open(f"{realm}/auth?{query}").read().decode()
        form = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', page)
        if not form:
            raise SystemExit("форма входа Keycloak не найдена")
        data = urllib.parse.urlencode({"username": user, "password": password, "credentialId": ""}).encode()
        location = ""
        try:
            self._opener(follow=False).open(html.unescape(form.group(1)), data)
        except urllib.error.HTTPError as e:
            location = e.headers.get("Location", "")
        code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query).get("code", [""])[0]
        if not code:
            raise SystemExit("вход не удался (неверный логин или пароль?)")
        body = urllib.parse.urlencode(
            {
                "grant_type": "authorization_code",
                "client_id": CLIENT,
                "code": code,
                "redirect_uri": redirect,
                "code_verifier": verifier,
            }
        ).encode()
        self.token = json.loads(self._opener().open(f"{realm}/token", body).read())["access_token"]

    def call(
        self, method: str, path: str, body: Any = None, raw: bytes | None = None, ctype: str = "application/json"
    ) -> Any:
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        if data is not None:
            req.add_header("Content-Type", ctype)
        try:
            out = self._opener().open(req).read()
        except urllib.error.HTTPError as e:
            if e.code in (404, 409):
                return {"_status": e.code, **json.loads(e.read() or b"{}")}
            raise SystemExit(f"{method} {path} → {e.code}: {e.read()[:300]!r}") from e
        return json.loads(out) if out else {}

    def upload(self, package_id: str, files: list[Path]) -> Any:
        boundary = secrets.token_hex(12)
        parts = b""
        for f in files:
            head = (
                f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="{f.name}"\r\n'
                "Content-Type: application/octet-stream\r\n\r\n"
            )
            parts += head.encode() + f.read_bytes() + b"\r\n"
        parts += f"--{boundary}--\r\n".encode()
        return self.call(
            "POST",
            f"/package/api/v1/packages/{package_id}/files",
            raw=parts,
            ctype=f"multipart/form-data; boundary={boundary}",
        )


def inn_of(labels: dict[str, Any]) -> str:
    for segs in labels["files"].values():
        for s in segs:
            if s["type"] == "APPLICATION":
                return str(s["fields"]["inn"])
    raise SystemExit("в разметке нет заявления")


def wait(api: Api, package_id: str, timeout: int) -> dict[str, Any]:
    started = time.monotonic()
    while True:
        pkg = api.call("GET", f"/package/api/v1/packages/{package_id}")
        stages = [f["stage"] for f in pkg.get("files", [])]
        print(f"  {int(time.monotonic() - started)} с: {stages} протокол={bool(pkg.get('protocol'))}", flush=True)
        if stages and all(s in TERMINAL for s in stages) and pkg.get("protocol"):
            return dict(pkg)
        if time.monotonic() - started > timeout:
            raise SystemExit(f"не дождались протокола за {timeout} с")
        time.sleep(10)


def compare(api: Api, pkg: dict[str, Any], labels: dict[str, Any]) -> tuple[list[str], int]:
    lines = ["| Файл | Ожидали | Получили | Уверенность |", "|---|---|---|---|"]
    ok = total = 0
    for f in pkg["files"]:
        want = [s["type"] for s in labels["files"].get(f["name"], [])]
        docs = f.get("documents", [])
        got = [str(d.get("type")) for d in docs]
        conf = [str(d.get("typeConfidence")) for d in docs]
        total += len(want)
        ok += sum(1 for a, b in zip(want, got, strict=False) if a == b)
        lines.append(f"| {f['name']} | {', '.join(want)} | {', '.join(got)} | {', '.join(conf)} |")
    proto = api.call("GET", f"/rules/api/v1/packages/{pkg['packageId']}/protocol")
    lines += [
        "",
        f"Вид документа: {ok}/{total} верно",
        "",
        f"Протокол: {proto.get('status')}",
        "",
        "| Правило | Ожидали | Получили |",
        "|---|---|---|",
    ]
    got_rules = {r["rule"]: r["status"] for r in proto.get("results", [])}
    wrong_rules = 0
    for rule, want_status in labels.get("expectedRules", {}).items():
        mark = "" if got_rules.get(rule) == want_status else " ✖"
        wrong_rules += bool(mark)
        lines.append(f"| {rule} | {want_status} | {got_rules.get(rule)}{mark} |")
    return lines, (total - ok) + wrong_rules


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--api", required=True)
    p.add_argument("--dir", required=True, type=Path)
    p.add_argument("--user", default="specialist")
    p.add_argument("--program", default="DEMO-2026")
    p.add_argument("--timeout", type=int, default=1800)
    p.add_argument("--report", type=Path)
    p.add_argument("--verify", action="store_true")
    p.add_argument("--strict", action="store_true", help="код выхода 1 при любом расхождении с разметкой")
    a = p.parse_args()
    labels = json.loads((a.dir / "labels.json").read_text(encoding="utf-8"))
    api = Api(a.api, a.verify)
    api.login(a.user, os.environ.get("EVAL_PASSWORD", ""))
    inn = inn_of(labels)
    if api.call("GET", f"/package/api/v1/organizations?inn={inn}").get("_status") == 404:
        api.call("POST", "/package/api/v1/organizations", {"inn": inn})
    package_id = api.call("POST", "/package/api/v1/packages", {"orgInn": inn, "programCode": a.program})["packageId"]
    print(f"пакет {package_id}", flush=True)
    files = sorted(f for f in a.dir.iterdir() if f.suffix.lower() in {".pdf", ".zip", ".png", ".jpg"})
    up = api.upload(package_id, files)
    if up.get("rejected"):
        print(f"отклонено: {up['rejected']}", file=sys.stderr)
    pkg = wait(api, package_id, a.timeout)
    lines, mismatches = compare(api, pkg, labels)
    report = "\n".join([f"# eval {a.dir.name}: пакет {package_id}", "", *lines, "", f"Расхождений: {mismatches}"])
    print(report)
    if a.report:
        a.report.write_text(report + "\n", encoding="utf-8")
    if a.strict and mismatches:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
