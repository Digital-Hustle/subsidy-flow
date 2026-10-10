from decimal import Decimal

from bench_llm import extract_schema, norm, page_types, registry, segment_schema


def test_money_is_normalized_like_docproc() -> None:
    assert norm("money", "1 602 532,80 руб.") == Decimal("1602532.80")
    assert norm("money", "1602532.80") == Decimal("1602532.80")


def test_requisites_keep_only_digits() -> None:
    assert norm("inn", "49 1417 7760") == "4914177760"


def test_segments_are_spread_over_pages() -> None:
    got = page_types([{"fromPage": 1, "toPage": 1, "type": "APPLICATION"},
                      {"fromPage": 2, "toPage": 3, "type": "EGRUL_EXTRACT"}], 3)
    assert got == ["APPLICATION", "EGRUL_EXTRACT", "EGRUL_EXTRACT"]


def test_schemas_follow_registry() -> None:
    types = registry()
    codes = segment_schema(types)["properties"]["segments"]["items"]["properties"]["type"]["enum"]
    assert len(codes) == len(types)
    assert extract_schema(types[0])["required"] == ["data", "confidence"]
