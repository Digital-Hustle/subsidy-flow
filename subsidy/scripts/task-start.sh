#!/usr/bin/env bash
# Старт задачи: свежий main → ветка → отдельный worktree → статус doing в TASKS.md → push → draft PR.
# Использование (из subsidy/): scripts/task-start.sh RUL-02 FR-40 arithmetic [feat|fix|docs|test|refactor|ci|chore|contract]
# Draft PR = заявка на задачу: все видят, кто что делает (gh pr list --draft). Подробно — docs/WORKFLOW.md.
set -euo pipefail
TASK="${1:?ID задачи из TASKS.md, например RUL-02}"; FR="${2:?ID требования, например FR-40}"
SLUG="${3:?короткий slug латиницей}"; TYPE="${4:-feat}"
ROOT="$(git rev-parse --show-toplevel)"; cd "$ROOT"
BR="$TYPE/$TASK-$SLUG"; WT="$(dirname "$ROOT")/wt-$TASK"

file="$(git grep -lE "^\| *$TASK *\|" -- 'subsidy/**/TASKS.md' | head -1 || true)"
[ -n "$file" ] || { echo "✖ $TASK нет ни в одном TASKS.md — сначала добавь строку (владелец модуля)"; exit 1; }
open_pr="$(command -v gh >/dev/null && gh pr list --state open --search "$TASK in:title" --json url -q '.[0].url' 2>/dev/null || true)"
[ -z "$open_pr" ] || { echo "✖ по $TASK уже есть открытый PR: $open_pr — продолжай его (docs/WORKFLOW.md §3)"; exit 1; }

git fetch -q origin main
git worktree add -q -b "$BR" "$WT" origin/main
cd "$WT"
# последний столбец строки задачи = статус
sed -i -E "s#^(\| *$TASK *\|.*\| *)[^|]*(\| *)\$#\1doing \`$BR\` \2#" "$file"
git add "$file" && git commit -q -m "chore(tasks): $TASK doing ($FR)"
git push -q -u origin "$BR"

scope="$(sed -nE 's#^subsidy/(services/([a-z]+)|(frontend))/.*#\2\3#p' <<<"$file")"; scope="${scope:-subsidy}"
TITLE="$TYPE($scope): $FR $TASK $SLUG"
if command -v gh >/dev/null; then
  BODY="$(mktemp)"; sed "s/{{ID}}/$FR $TASK/g" "$ROOT/.github/PULL_REQUEST_TEMPLATE.md" > "$BODY"
  gh pr create --draft --base main --head "$BR" --title "$TITLE" --body-file "$BODY" --assignee @me
  rm -f "$BODY"
else
  echo "⚠ нет gh: открой draft PR вручную — ветка $BR, заголовок «$TITLE»"
fi
echo "✔ worktree: $WT (cd $WT/subsidy). Дальше: тест → код → scripts/ci-local.sh quick → commit → push"
