"""Генератор синтетического пакета: 8 видов документов вымышленной организации, PDF с текстовым слоем.

python -m synthetic.gen --out /tmp/pkg --seed 7 [--date 2026-10-09] [--merged] [--defect items]
Имена файлов нейтральные (doc-XXXX.pdf), порядок перемешан: вид определяется только по содержимому.
Разметка — labels.json: {file: [{fromPage, toPage, type, fields}]} и ожидаемые статусы правил.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import random
from dataclasses import dataclass, field
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path
from typing import Any

from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen.canvas import Canvas

from synthetic import requisites as rq
from synthetic.words import rubles

FONTS = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/liberation-sans/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
CENT = Decimal("0.01")
NAMES = ["Вектор", "Северный ветер", "Градиент", "Кедр", "Орбита", "Полярис", "Сфера", "Магистраль"]
GOODS = [
    ("Станок токарный с ЧПУ", "шт"),
    ("Фрезерный обрабатывающий центр", "шт"),
    ("Пусконаладочные работы", "усл"),
    ("Доставка оборудования", "усл"),
    ("Комплект оснастки", "компл"),
    ("Обучение операторов", "усл"),
]
HEADS = ["Смирнов Олег Петрович", "Кузнецова Ирина Викторовна", "Волков Денис Андреевич", "Орлова Мария Сергеевна"]


def money(v: Decimal) -> str:
    s = f"{v.quantize(CENT):,.2f}"
    return s.replace(",", " ").replace(".", ",")


def font() -> str:
    for p in FONTS:
        if Path(p).exists():
            pdfmetrics.registerFont(TTFont("Body", p))
            return "Body"
    raise SystemExit("нет TTF-шрифта с кириллицей (fonts-liberation)")


@dataclass
class Org:
    name: str
    inn: str
    kpp: str
    ogrn: str
    bik: str
    account: str
    head: str
    address: str


@dataclass
class Doc:
    type: str
    title: str
    lines: list[str]
    fields: dict[str, Any]
    table: list[list[str]] = field(default_factory=list)
    after: list[str] = field(default_factory=list)


def org(rnd: random.Random, kind: str) -> Org:
    inn = rq.inn10(rnd)
    b = rq.bik(rnd)
    return Org(
        name=f"{kind} «{rnd.choice(NAMES)}-{rnd.randint(10, 99)}»",
        inn=inn,
        kpp=rq.kpp(inn, rnd),
        ogrn=rq.ogrn13(rnd, rnd.randint(2012, 2021)),
        bik=b,
        account=rq.account(b, rnd),
        head=rnd.choice(HEADS),
        address=f"г. Синтетск, ул. Тестовая, д. {rnd.randint(1, 120)}",
    )


def weekday(d: dt.date) -> dt.date:
    while d.weekday() >= 5:
        d -= dt.timedelta(days=1)
    return d


def build(seed: int, today: dt.date, defect: str | None = None) -> list[Doc]:
    rnd = random.Random(seed)
    app, seller = org(rnd, "ООО"), org(rnd, "ООО")
    items = []
    for name, unit in rnd.sample(GOODS, 3):
        qty = rnd.randint(1, 3) if unit == "шт" else 1
        price = Decimal(rnd.randint(1_500_000, 60_000_000)) / 100
        items.append((name, unit, qty, price, (price * qty).quantize(CENT)))
    total = sum((i[4] for i in items), Decimal("0"))
    vat = (total * 20 / 120).quantize(CENT, ROUND_HALF_UP)
    subsidy = (total * Decimal("0.50")).quantize(CENT, ROUND_HALF_UP)
    d_contract = weekday(today - dt.timedelta(days=70))
    d_invoice = weekday(today - dt.timedelta(days=64))
    d_pay = weekday(today - dt.timedelta(days=58))
    num = rnd.randint(10, 999)
    rows = [["№", "Наименование", "Ед.", "Кол-во", "Цена, руб.", "Сумма, руб."]] + [
        [str(i + 1), n, u, str(q), money(p), money(a)] for i, (n, u, q, p, a) in enumerate(items)
    ]
    inv_total = total + Decimal("1000.00") if defect == "items" else total
    fio = app.head
    docs = [
        Doc(
            "APPLICATION",
            "ЗАЯВЛЕНИЕ о предоставлении субсидии",
            [
                "В министерство экономического развития (демо-программа DEMO-2026)",
                f"Заявитель: {app.name}",
                f"ИНН {app.inn}  КПП {app.kpp}  ОГРН {app.ogrn}",
                f"Адрес: {app.address}",
                "Прошу предоставить субсидию на возмещение части затрат на приобретение оборудования",
                f"в размере {money(subsidy)} руб. ({rubles(subsidy)}).",
                f"Банковские реквизиты: р/с {app.account}, БИК {app.bik}",
                f"Дата: {today:%d.%m.%Y}",
                f"Генеральный директор __________ {fio}",
            ],
            {"inn": app.inn, "ogrn": app.ogrn, "requestedAmount": str(subsidy), "signerName": fio},
        ),
        Doc(
            "EGRUL_EXTRACT",
            "ВЫПИСКА из Единого государственного реестра юридических лиц",
            [
                f"Дата формирования выписки: {today - dt.timedelta(days=5):%d.%m.%Y}",
                f"Полное наименование: Общество с ограниченной ответственностью «{app.name.split('«')[1]}",
                f"ОГРН {app.ogrn}   ИНН {app.inn}   КПП {app.kpp}",
                f"Дата регистрации: 14.03.20{app.ogrn[1:3]}",
                "Статус: действующее",
                f"Адрес юридического лица: {app.address}",
                "Сведения о лице, имеющем право без доверенности действовать от имени юридического лица:",
                f"{fio}, генеральный директор",
                "Основной вид деятельности: 25.62 Обработка металлических изделий механическая",
                "Дополнительные виды деятельности: 28.41, 33.12",
                "Выписка сформирована ФНС России",
            ],
            {"inn": app.inn, "ogrn": app.ogrn, "status": "действующее", "okvedMain": "25.62"},
        ),
        Doc(
            "TAX_CERTIFICATE",
            "СПРАВКА об исполнении налогоплательщиком обязанности по уплате налогов",
            [
                "Код по КНД 1120101",
                f"Выдана: {app.name}, ИНН {app.inn}",
                f"по состоянию на {today - dt.timedelta(days=8):%d.%m.%Y}",
                "не имеет неисполненную обязанность по уплате налогов, сборов, страховых взносов, пеней, штрафов,",
                "процентов, подлежащих уплате в соответствии с законодательством",
                "Российской Федерации о налогах и сборах.",
                f"Дата выдачи: {today - dt.timedelta(days=7):%d.%m.%Y}",
                "Инспекция ФНС России № 99 по г. Синтетску",
            ],
            {"inn": app.inn, "hasDebt": False},
        ),
        Doc(
            "FIN_STATEMENTS",
            "БУХГАЛТЕРСКИЙ БАЛАНС на 31 декабря 2025 г.",
            [
                "Форма по ОКУД 0710001",
                f"Организация: {app.name}   ИНН {app.inn}",
                "Единица измерения: тыс. руб.",
                "Код строки 1300 — Итого капитал: 18 420",
                "Код строки 1600 — БАЛАНС (актив): 52 310",
                "Код строки 1700 — БАЛАНС (пассив): 52 310",
                "ОТЧЁТ О ФИНАНСОВЫХ РЕЗУЛЬТАТАХ за 2025 г.",
                "Код строки 2110 — Выручка: 96 540",
                "Код строки 2400 — Чистая прибыль (убыток): 4 870",
            ],
            {"inn": app.inn},
        ),
        Doc(
            "COST_ESTIMATE",
            "СМЕТА ЗАТРАТ и расчёт размера субсидии",
            [f"Получатель: {app.name}, ИНН {app.inn}", "Статьи затрат на приобретение оборудования:"],
            {"total": str(total), "subsidyAmount": str(subsidy)},
            rows,
            [
                f"Итого затрат: {money(total)} руб.",
                "Ставка возмещения: 50 %",
                f"Размер субсидии: {money(subsidy)} руб.",
            ],
        ),
        Doc(
            "CONTRACT",
            f"ДОГОВОР ПОСТАВКИ № {num}-П",
            [
                f"г. Синтетск                                                     {d_contract:%d.%m.%Y}",
                f"{seller.name} (ИНН {seller.inn}), именуемое «Поставщик», в лице генерального директора, и",
                f"{app.name} (ИНН {app.inn}), именуемое «Покупатель», заключили настоящий договор:",
                "1. Предмет договора: поставка оборудования и выполнение пусконаладочных работ по спецификации.",
                f"2. Цена договора составляет {money(total)} руб. ({rubles(total)}), в т.ч. НДС 20 % {money(vat)} руб.",
                "3. Оплата — в течение 10 рабочих дней после выставления счёта.",
                f"Поставщик: {seller.name}, р/с {seller.account}, БИК {seller.bik}",
                f"Покупатель: {app.name}, р/с {app.account}, БИК {app.bik}",
            ],
            {"buyerInn": app.inn, "sellerInn": seller.inn, "amount": str(total)},
        ),
        Doc(
            "INVOICE",
            f"СЧЁТ НА ОПЛАТУ № {num} от {d_invoice:%d.%m.%Y}",
            [
                f"Поставщик: {seller.name}, ИНН {seller.inn}, КПП {seller.kpp}",
                f"Покупатель: {app.name}, ИНН {app.inn}",
                f"Основание: договор поставки № {num}-П от {d_contract:%d.%m.%Y}",
            ],
            {"sellerInn": seller.inn, "buyerInn": app.inn, "total": str(inv_total)},
            rows,
            [
                f"Итого: {money(inv_total)}",
                f"В том числе НДС (20 %): {money(vat)}",
                f"Всего к оплате: {rubles(inv_total)}",
            ],
        ),
        Doc(
            "PAYMENT_ORDER",
            f"ПЛАТЁЖНОЕ ПОРУЧЕНИЕ № {rnd.randint(100, 9999)}",
            [
                f"Дата: {d_pay:%d.%m.%Y}     Вид платежа: электронно",
                f"Сумма прописью: {rubles(total)}",
                f"Сумма: {money(total)}",
                f"Плательщик: {app.name}, ИНН {app.inn}, сч. № {app.account}",
                f"Банк плательщика: БИК {app.bik}",
                f"Получатель: {seller.name}, ИНН {seller.inn}, сч. № {seller.account}",
                f"Банк получателя: БИК {seller.bik}",
                f"Назначение платежа: оплата по счёту № {num} от {d_invoice:%d.%m.%Y}, в т.ч. НДС 20 % {money(vat)}",
                "Списано со сч. плат.: " + f"{d_pay:%d.%m.%Y}",
            ],
            {"payerInn": app.inn, "payeeInn": seller.inn, "amount": str(total)},
        ),
    ]
    return docs


def draw(c: Canvas, f: str, doc: Doc) -> None:
    w, h = A4
    y = h - 60
    c.setFont(f, 13)
    c.drawString(50, y, doc.title)
    y -= 30
    c.setFont(f, 10)
    for line in doc.lines:
        c.drawString(50, y, line)
        y -= 17
    if doc.table:
        xs = [50, 75, 300, 345, 395, 470, w - 40]
        y -= 6
        for row in doc.table:
            c.line(xs[0], y + 13, xs[-1], y + 13)
            for i, cell in enumerate(row):
                c.drawString(xs[i] + 3, y, cell[:44])
            y -= 18
        c.line(xs[0], y + 13, xs[-1], y + 13)
        top = y + 13 + 18 * len(doc.table)
        for x in xs:
            c.line(x, top, x, y + 13)
        y -= 10
    for line in doc.after:
        c.drawString(50, y, line)
        y -= 17
    c.showPage()


def write(out: Path, seed: int, today: dt.date, merged: bool, defect: str | None) -> dict[str, Any]:
    out.mkdir(parents=True, exist_ok=True)
    f = font()
    docs = build(seed, today, defect)
    rnd = random.Random(seed + 1)
    groups: list[list[Doc]] = [[d] for d in docs]
    if merged:  # заявление + выписка + справка в одном PDF (сегментация по содержимому)
        groups = [docs[:3]] + [[d] for d in docs[3:]]
    rnd.shuffle(groups)
    labels: dict[str, Any] = {"seed": seed, "date": today.isoformat(), "defect": defect, "files": {}}
    for g in groups:
        name = f"doc-{rnd.randint(1000, 9999)}.pdf"
        c = Canvas(str(out / name), pagesize=A4)
        c.setTitle("document")
        for d in g:
            draw(c, f, d)
        c.save()
        labels["files"][name] = [
            {"fromPage": i + 1, "toPage": i + 1, "type": d.type, "fields": d.fields} for i, d in enumerate(g)
        ]
    labels["expectedRules"] = {
        "SUM_INVOICE_ITEMS": "FAILED" if defect == "items" else "PASSED",
        "CROSS_INN": "PASSED",
        "SUM_SUBSIDY_CAP": "PASSED",
    }
    (out / "labels.json").write_text(json.dumps(labels, ensure_ascii=False, indent=1), encoding="utf-8")
    return labels


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out", required=True, type=Path)
    p.add_argument("--seed", type=int, default=1)
    p.add_argument("--date", type=dt.date.fromisoformat, default=dt.date.today())
    p.add_argument("--merged", action="store_true")
    p.add_argument("--defect", choices=["items"])
    a = p.parse_args()
    labels = write(a.out, a.seed, a.date, a.merged, a.defect)
    print(json.dumps({k: [s["type"] for s in v] for k, v in labels["files"].items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
