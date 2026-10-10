"""Реквизиты с верными контрольными суммами (docs/DOC-TYPES.md §Контрольные суммы)."""

from __future__ import annotations

import random

INN10_W = [2, 4, 10, 3, 5, 9, 4, 6, 8]
ACC_W = [7, 1, 3]


def inn10(rnd: random.Random) -> str:
    d = [rnd.randint(0, 9) for _ in range(9)]
    d[0] = rnd.randint(1, 9)
    return "".join(map(str, d)) + str(sum(a * b for a, b in zip(d, INN10_W, strict=True)) % 11 % 10)


def inn_valid(inn: str) -> bool:
    if len(inn) != 10 or not inn.isdigit():
        return False
    d = [int(c) for c in inn]
    return sum(a * b for a, b in zip(d[:9], INN10_W, strict=True)) % 11 % 10 == d[9]


def ogrn13(rnd: random.Random, year: int) -> str:
    base = "1" + f"{year % 100:02d}" + "".join(str(rnd.randint(0, 9)) for _ in range(9))
    return base + str(int(base) % 11 % 10)


def ogrn_valid(ogrn: str) -> bool:
    return len(ogrn) == 13 and ogrn.isdigit() and int(ogrn[:12]) % 11 % 10 == int(ogrn[12])


def kpp(inn: str, rnd: random.Random) -> str:
    return inn[:4] + "01" + f"{rnd.randint(1, 999):03d}"


def bik(rnd: random.Random) -> str:
    return "04" + "".join(str(rnd.randint(0, 9)) for _ in range(4)) + f"{rnd.randint(50, 999):03d}"


def account_valid(bik_: str, account: str) -> bool:
    digits = bik_[-3:] + account
    return len(account) == 20 and sum(int(c) * ACC_W[i % 3] for i, c in enumerate(digits)) % 10 == 0


def account(bik_: str, rnd: random.Random) -> str:
    """Р/с 40702810… с ключом ЦБ (9-я цифра подбирается)."""
    body = "40702810" + "X" + "".join(str(rnd.randint(0, 9)) for _ in range(11))
    for key in range(10):
        acc = body.replace("X", str(key))
        if account_valid(bik_, acc):
            return acc
    raise AssertionError("ключ счёта не найден")
