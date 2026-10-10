#!/usr/bin/env bash
# Сверяет contracts/openapi/*.json с закоммиченными без учёта порядка ключей (springdoc выдаёт коды ответов в разном порядке).
#   scripts/check-openapi.sh   — после ./gradlew exportOpenApi; код 1, если контракт по смыслу изменился
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0
for f in contracts/openapi/*.json; do
  if ! git cat-file -e "HEAD:./$f" 2>/dev/null; then echo "✖ новый контракт не закоммичен: $f"; fail=1; continue; fi
  if ! diff -q <(git show "HEAD:./$f" | jq -S .) <(jq -S . "$f") >/dev/null; then
    echo "✖ $f устарел — закоммить перегенерированный файл"; { diff <(git show "HEAD:./$f" | jq -S .) <(jq -S . "$f") | head -40 || true; }; fail=1
  else git checkout -q -- "$f" 2>/dev/null || true; fi
done
exit $fail
