import datetime as dt
import json
import random
from decimal import Decimal
from pathlib import Path

import pdfplumber

from synthetic import gen
from synthetic import requisites as rq
from synthetic.words import rubles

DAY = dt.date(2026, 10, 9)


def test_requisites_checksums() -> None:
    rnd = random.Random(3)
    for _ in range(50):
        assert rq.inn_valid(rq.inn10(rnd))
        assert rq.ogrn_valid(rq.ogrn13(rnd, 2018))
        b = rq.bik(rnd)
        assert len(b) == 9
        assert rq.account_valid(b, rq.account(b, rnd))
    assert rq.inn_valid("7707083893")
    assert not rq.inn_valid("7707083894")


def test_rubles_in_words() -> None:
    assert rubles(Decimal("1730175.86")) == "Один миллион семьсот тридцать тысяч сто семьдесят пять рублей 86 копеек"
    assert rubles(Decimal("21.01")) == "Двадцать один рубль 01 копейка"
    assert rubles(Decimal("2002.00")) == "Две тысячи два рубля 00 копеек"


def test_package_has_eight_types_and_consistent_sums(tmp_path: Path) -> None:
    labels = gen.write(tmp_path, 11, DAY, merged=True, defect=None)
    types = sorted(s["type"] for segs in labels["files"].values() for s in segs)
    assert types == sorted(
        [
            "APPLICATION",
            "EGRUL_EXTRACT",
            "TAX_CERTIFICATE",
            "FIN_STATEMENTS",
            "COST_ESTIMATE",
            "CONTRACT",
            "INVOICE",
            "PAYMENT_ORDER",
        ]
    )
    assert any(len(segs) == 3 for segs in labels["files"].values())
    assert all(name.startswith("doc-") for name in labels["files"])
    fields = {s["type"]: s["fields"] for segs in labels["files"].values() for s in segs}
    total = Decimal(fields["COST_ESTIMATE"]["total"])
    assert Decimal(fields["INVOICE"]["total"]) == total == Decimal(fields["PAYMENT_ORDER"]["amount"])
    assert Decimal(fields["APPLICATION"]["requestedAmount"]) == (total / 2).quantize(Decimal("0.01"))
    saved = json.loads((tmp_path / "labels.json").read_text(encoding="utf-8"))
    assert saved["expectedRules"]["SUM_INVOICE_ITEMS"] == "PASSED"


def test_pdf_has_text_layer_and_neutral_name(tmp_path: Path) -> None:
    labels = gen.write(tmp_path, 5, DAY, merged=False, defect="items")
    inv = next(n for n, segs in labels["files"].items() if segs[0]["type"] == "INVOICE")
    with pdfplumber.open(tmp_path / inv) as pdf:
        text = pdf.pages[0].extract_text()
    assert "СЧЁТ НА ОПЛАТУ" in text
    assert "INVOICE" not in inv
    assert labels["expectedRules"]["SUM_INVOICE_ITEMS"] == "FAILED"
