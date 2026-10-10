"""Замер модели на синтетических пакетах тем же запросом, что и docproc (LLM-08, docs/LLM.md).

Без поднятия стенда: текст страниц берётся из PDF (pdfplumber), промпты и схемы — из
ресурсов docproc (prompts/segment.v1.md, prompts/extract.v1.md, doc-types/registry.json),
параметры запроса — как в OllamaAdapter / OpenAiAdapter (temperature 0, seed 42, num_ctx 8192).

  uv run --group dev python bench_llm.py --provider ollama --base http://localhost:11434 --model qwen3:4b --seeds 3
  LLM_API_KEY=... uv run --group dev python bench_llm.py --provider openai --base https://.../v1 --model ... --seeds 3

Итог — строка для таблицы «Выбор локальной модели» в docs/LLM.md: время на документ,
точность вида (по страницам) и точность полей (по размеченным полям синтетики).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import time
import urllib.request
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

import pdfplumber

from synthetic.gen import write

RES = Path(__file__).resolve().parent.parent / "services/docproc/src/main/resources"
MAX_CHARS_PER_PAGE = 1500
MAX_CHARS_PER_DOCUMENT = 12000
SEED = 42
NUM_CTX = 8192
TIMEOUT = 900


def registry() -> list[dict[str, Any]]:
    return json.loads((RES / "doc-types/registry.json").read_text(encoding="utf-8"))


def prompt(name: str) -> str:
    return (RES / f"prompts/{name}.md").read_text(encoding="utf-8")


def segment_schema(types: list[dict[str, Any]]) -> dict[str, Any]:
    item = {
        "type": "object",
        "properties": {
            "fromPage": {"type": "integer", "minimum": 1},
            "toPage": {"type": "integer", "minimum": 1},
            "type": {"type": "string", "enum": [t["code"] for t in types]},
            "confidence": {"type": "number"},
            "evidence": {"type": "string"},
        },
        "required": ["fromPage", "toPage", "type", "confidence", "evidence"],
        "additionalProperties": False,
    }
    return {
        "type": "object",
        "properties": {"segments": {"type": "array", "minItems": 1, "items": item}},
        "required": ["segments"],
        "additionalProperties": False,
    }


def extract_schema(doc_type: dict[str, Any]) -> dict[str, Any]:
    return {
        "type": "object",
        "properties": {
            "data": doc_type["schema"],
            "confidence": {"type": "object", "additionalProperties": {"type": "number"}},
        },
        "required": ["data", "confidence"],
    }


class Llm:
    def __init__(self, provider: str, base: str, model: str) -> None:
        self.provider, self.base, self.model = provider, base.rstrip("/"), model
        self.key = os.environ.get("LLM_API_KEY", "")

    def ask(self, system: str, user: str, schema: dict[str, Any]) -> dict[str, Any]:
        msgs = [{"role": "system", "content": system}, {"role": "user", "content": user}]
        if self.provider == "ollama":
            url = self.base + "/api/chat"
            body: dict[str, Any] = {
                "model": self.model, "messages": msgs, "format": schema, "stream": False, "think": False,
                "keep_alive": "30m", "options": {"temperature": 0, "seed": SEED, "num_ctx": NUM_CTX},
            }
        else:
            url = self.base + "/chat/completions"
            body = {
                "model": self.model, "messages": msgs, "temperature": 0, "seed": SEED,
                "response_format": {"type": "json_schema",
                                    "json_schema": {"name": "answer", "strict": False, "schema": schema}},
            }
        rq = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST",
                                    headers={"Content-Type": "application/json",
                                             **({"Authorization": f"Bearer {self.key}"} if self.key else {})})
        with urllib.request.urlopen(rq, timeout=TIMEOUT) as rs:
            answer = json.loads(rs.read())
        text = answer["message"]["content"] if self.provider == "ollama" else \
            answer["choices"][0]["message"]["content"]
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip())
        return json.loads(text)


def pages_of(pdf: Path) -> list[str]:
    with pdfplumber.open(pdf) as doc:
        return [(p.extract_text() or "").strip() for p in doc.pages]


def segment_user(pages: list[str]) -> str:
    out = [f"Страниц в файле: {len(pages)}\n\n"]
    for i, text in enumerate(pages, 1):
        text = text or "[страница без текстового слоя — см. изображение]"
        if len(text) > MAX_CHARS_PER_PAGE:
            text = text[:MAX_CHARS_PER_PAGE] + " …"
        out.append(f"=== Страница {i} ===\n{text}\n\n")
    return "".join(out)


def page_types(segments: list[dict[str, Any]], pages: int) -> list[str]:
    result = ["OTHER"] * pages
    for seg in segments:
        lo, hi = max(1, int(seg.get("fromPage", 1))), min(pages, int(seg.get("toPage", 1)))
        for p in range(lo, hi + 1):
            result[p - 1] = seg.get("type", "OTHER")
    return result


def norm(kind: str, value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    s = str(value).strip()
    if kind in {"inn", "kpp", "ogrn", "bik", "account"}:
        return re.sub(r"\D", "", s)
    if kind == "money":
        try:
            s = re.sub(r"(руб(лей|\.)?|₽)", "", s, flags=re.IGNORECASE)
            return Decimal(re.sub(r"[\s\u00a0]", "", s).replace(",", "."))
        except InvalidOperation:
            return s
    if s.lower() in {"true", "false"}:
        return s.lower() == "true"
    return re.sub(r"\s+", " ", s).casefold()


def run(llm: Llm, seeds: int) -> dict[str, Any]:
    types = registry()
    by_code = {t["code"]: t for t in types}
    seg_system = prompt("segment.v1").replace(
        "{{TYPES}}", "\n".join(f"- {t['code']} — {t['name']} — {t['hints']}" for t in types))
    pages_total = pages_ok = fields_total = fields_ok = docs = 0
    seconds = 0.0
    misses: list[str] = []
    for seed in range(1, seeds + 1):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            labels = write(out, seed, __import__("datetime").date.today(), merged=seed % 2 == 1, defect=None)
            for name, truth in labels["files"].items():
                pages = pages_of(out / name)
                started = time.monotonic()
                answer = llm.ask(seg_system, segment_user(pages), segment_schema(types))
                got = page_types(answer.get("segments", []), len(pages))
                for seg in truth:
                    for p in range(seg["fromPage"], seg["toPage"] + 1):
                        pages_total += 1
                        if got[p - 1] == seg["type"]:
                            pages_ok += 1
                        else:
                            misses.append(f"seed {seed} {name} стр.{p}: {seg['type']} → {got[p - 1]}")
                    doc_type = by_code[seg["type"]]
                    text = "\n\n".join(pages[seg["fromPage"] - 1:seg["toPage"]])[:MAX_CHARS_PER_DOCUMENT]
                    system = prompt("extract.v1").replace("{{TYPE_NAME}}", doc_type["name"]) \
                        .replace("{{TYPE_CODE}}", doc_type["code"])
                    data = llm.ask(system, text, extract_schema(doc_type)).get("data", {}) or {}
                    docs += 1
                    for field, expected in seg["fields"].items():
                        kind = doc_type["kinds"].get(field, "text")
                        fields_total += 1
                        if norm(kind, data.get(field)) == norm(kind, expected):
                            fields_ok += 1
                        else:
                            misses.append(f"seed {seed} {seg['type']}.{field}: {expected!r} → {data.get(field)!r}")
                seconds += time.monotonic() - started
    return {
        "model": f"{llm.provider} · {llm.model}",
        "documents": docs,
        "secondsPerDocument": round(seconds / max(docs, 1), 1),
        "typeAccuracy": f"{pages_ok}/{pages_total}",
        "fieldAccuracy": f"{fields_ok}/{fields_total}",
        "misses": misses,
    }


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--provider", choices=["ollama", "openai"], default="ollama")
    p.add_argument("--base", default="http://localhost:11434")
    p.add_argument("--model", required=True)
    p.add_argument("--seeds", type=int, default=2, help="сколько синтетических пакетов (нечётные — со склейкой)")
    a = p.parse_args()
    report = run(Llm(a.provider, a.base, a.model), a.seeds)
    json.dump(report, sys.stdout, ensure_ascii=False, indent=1)
    print()


if __name__ == "__main__":
    main()
