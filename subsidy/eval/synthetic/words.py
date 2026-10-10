"""Сумма прописью для синтетических документов (рубли и копейки)."""

from __future__ import annotations

from decimal import Decimal

_ONES = ["", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"]
_ONES_F = ["", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"]
_TEENS = [
    "десять",
    "одиннадцать",
    "двенадцать",
    "тринадцать",
    "четырнадцать",
    "пятнадцать",
    "шестнадцать",
    "семнадцать",
    "восемнадцать",
    "девятнадцать",
]
_TENS = ["", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто"]
_HUNDREDS = ["", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот"]
_SCALES = [(("", "", ""), False), (("тысяча", "тысячи", "тысяч"), True), (("миллион", "миллиона", "миллионов"), False)]


def _plural(n: int, forms: tuple[str, str, str]) -> str:
    if n % 100 in range(11, 20):
        return forms[2]
    return forms[0] if n % 10 == 1 else forms[1] if n % 10 in (2, 3, 4) else forms[2]


def _triple(n: int, female: bool) -> list[str]:
    out = [_HUNDREDS[n // 100]]
    rest = n % 100
    if 10 <= rest < 20:
        out.append(_TEENS[rest - 10])
    else:
        out += [_TENS[rest // 10], (_ONES_F if female else _ONES)[rest % 10]]
    return [w for w in out if w]


def rubles(amount: Decimal) -> str:
    whole = int(amount)
    kop = int((amount - whole) * 100)
    parts: list[str] = []
    if whole == 0:
        parts = ["ноль"]
    for i, (forms, female) in reversed(list(enumerate(_SCALES))):
        chunk = whole // 1000**i % 1000
        if chunk:
            parts += _triple(chunk, female)
            if i:
                parts.append(_plural(chunk, forms))
    text = " ".join(parts) + " " + _plural(whole, ("рубль", "рубля", "рублей")) + f" {kop:02d} "
    text += _plural(kop, ("копейка", "копейки", "копеек"))
    return text[0].upper() + text[1:]
