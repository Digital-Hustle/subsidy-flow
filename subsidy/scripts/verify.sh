#!/usr/bin/env bash
# Запускает проверки только для затронутых частей монорепы.
# Использование: scripts/verify.sh [svc:<name>|services|frontend|contract|infra|all]...
set -euo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE"
REPO="$(git rev-parse --show-toplevel)"
BASE="${VERIFY_BASE:-origin/main}"
SHARED='^services/(libs/|build-logic/|gradle/|settings.gradle.kts|config/)'

parts=("$@")
if [ ${#parts[@]} -eq 0 ]; then
  git rev-parse --verify -q "$BASE" >/dev/null || BASE="$(git rev-list --max-parents=0 HEAD | tail -1)"
  changed="$( { git diff --relative --name-only "$BASE"...HEAD; git diff --relative --name-only; git diff --relative --name-only --cached; git ls-files --others --exclude-standard; } | sort -u)"
  changed="$(grep -vE '\.md$' <<<"$changed" || true)"   # только документация — проверять нечего
  if grep -qE "$SHARED" <<<"$changed"; then parts+=(services contract)
  else for s in $(grep -oE '^services/[a-z]+/' <<<"$changed" | cut -d/ -f2 | sort -u); do parts+=("svc:$s"); done
       grep -q '^services/' <<<"$changed" && parts+=(contract); fi
  grep -q '^frontend/' <<<"$changed" && parts+=(frontend)
  grep -q '^contracts/' <<<"$changed" && parts+=(contract frontend)
  grep -q '^infra/'    <<<"$changed" && parts+=(infra)
  [ ${#parts[@]} -eq 0 ] && { echo "verify: нет изменений в коде (только docs?) — ок"; exit 0; }
fi
[ "${parts[0]}" = "all" ] && parts=(services contract frontend infra)
mapfile -t parts < <(printf '%s\n' "${parts[@]}" | awk '!seen[$0]++')

run() { echo "▶ $*"; "$@"; }
java_svc() { [ -f "services/$1/build.gradle.kts" ]; }
py_svc()   { [ -f "services/$1/pyproject.toml" ]; }
check_py() { (cd "services/$1" && run uv sync --frozen -q && run uv run ruff check . && run uv run ruff format --check . \
               && run uv run mypy src && run uv run pytest -q); }
for p in "${parts[@]}"; do
  case "$p" in
    services) if [ -f services/settings.gradle.kts ]; then
                (cd services && run ./gradlew -q check && run node "$REPO/.agents/skills/backend-spring/scripts/be-check.mjs" .)
              fi
              for d in services/*/; do s=$(basename "$d"); py_svc "$s" && check_py "$s"; done; true ;;
    svc:*)    s="${p#svc:}"
              if java_svc "$s"; then (cd services && run ./gradlew -q ":$s:check" && run node "$REPO/.agents/skills/backend-spring/scripts/be-check.mjs" .)
              elif py_svc "$s"; then check_py "$s"; fi ;;
    contract) if [ -f services/settings.gradle.kts ]; then
                (cd services && run ./gradlew -q exportOpenApi)
                git diff --exit-code -- contracts/ \
                  || { echo "✖ contracts/ устарели — закоммить перегенерированные файлы"; exit 1; }
              fi ;;
    frontend) [ -f frontend/package.json ] && (cd frontend && run npm ci --silent && run npm run gen:api \
                && run npm run gen:tokens -- --check && run npm run lint && run npm run typecheck && run npm run build \
                && run node "$REPO/.agents/skills/frontend-fsd/scripts/fsd-check.mjs" src) ;;
    infra)    [ -f infra/docker-compose.yml ] && (cd infra && run docker compose --env-file .env.example config -q) ;;
    *) echo "неизвестная часть: $p"; exit 2 ;;
  esac
done
echo "✔ verify: ${parts[*]}"
