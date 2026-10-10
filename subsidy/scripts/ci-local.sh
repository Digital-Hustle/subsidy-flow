#!/usr/bin/env bash
# Локальный CI — те же проверки, что в GitHub Actions, теми же скриптами (docs/WORKFLOW.md §6).
#   scripts/ci-local.sh quick   verify.sh по изменённым частям + guard.sh (после каждого шага)
#   scripts/ci-local.sh [full]  verify.sh all + guard.sh + gitleaks (перед переводом PR в ready)
#   scripts/ci-local.sh act     весь workflow через nektos/act в docker (если правишь .github/)
set -uo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE"
ROOT="$(git rev-parse --show-toplevel)"
MODE="${1:-full}"; fail=0; skipped=()
step() { echo; echo "━━ $1"; shift; "$@" || { echo "✖ упало: $*"; fail=1; }; }
has_docker() { command -v docker >/dev/null && docker info >/dev/null 2>&1; }

case "$MODE" in
  quick)
    step "verify (изменённые части)" scripts/verify.sh
    step "guard" scripts/guard.sh ;;
  full)
    has_docker || skipped+=("Testcontainers-тесты (нет docker — Gradle IT упадут/пропустятся)")
    step "verify all" scripts/verify.sh all
    step "guard" scripts/guard.sh
    if command -v gitleaks >/dev/null; then step "gitleaks" gitleaks detect --source "$ROOT" --no-banner --redact
    elif has_docker; then step "gitleaks (docker)" docker run --rm -v "$ROOT:/repo" zricethezav/gitleaks:latest detect --source /repo --no-banner --redact
    else skipped+=("gitleaks"); fi ;;
  act)
    command -v act >/dev/null || { echo "✖ нет act: https://github.com/nektos/act (brew install act)"; exit 2; }
    has_docker || { echo "✖ act требует docker"; exit 2; }
    cd "$ROOT" && step "act pull_request" act pull_request -W ".github/workflows/$(basename "$CASE")-ci.yml" ;;
  *) echo "режим: quick | full | act"; exit 2 ;;
esac

echo
[ ${#skipped[@]} -gt 0 ] && { echo "⚠ пропущено локально (напиши в PR):"; printf '  - %s\n' "${skipped[@]}"; }
[ $fail -eq 0 ] && echo "✔ ci-local $MODE: зелёный" || echo "✖ ci-local $MODE: есть падения"
exit $fail
