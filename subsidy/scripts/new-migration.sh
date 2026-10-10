#!/usr/bin/env bash
# Новый Liquibase changeset с меткой времени (параллельные PR не конфликтуют, includeAll сортирует по имени).
# Использование: scripts/new-migration.sh <service> <slug>    например: scripts/new-migration.sh rules create-findings
set -euo pipefail
SVC="${1:?сервис: package|docproc|rules|assistant}"; SLUG="${2:?slug, например create-findings}"
[[ "$SLUG" =~ ^[a-z0-9-]+$ ]] || { echo "slug: только a-z0-9-"; exit 1; }
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -d "$CASE/services/$SVC" ] || { echo "нет сервиса services/$SVC"; exit 1; }
if [ -f "$CASE/services/$SVC/pyproject.toml" ]; then DIR="$CASE/services/$SVC/db/changelog/changesets"   # face: liquibase-контейнер
else DIR="$CASE/services/$SVC/src/main/resources/db/changelog/changesets"; fi
mkdir -p "$DIR"
TS=$(date -u +%Y%m%d%H%M%S); ID="$TS-$SLUG"
AUTHOR=$(git config user.name | tr '[:upper:] ' '[:lower:]-' || echo dev)
F="$DIR/$ID.sql"
cat > "$F" <<SQL
--liquibase formatted sql

--changeset $AUTHOR:$ID
-- требование: <FR-xx>


--rollback
SQL
echo "$F"
echo "После правки: cd services && ./gradlew :$SVC:update :$SVC:generateJooq"
