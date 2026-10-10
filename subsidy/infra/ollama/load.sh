#!/usr/bin/env bash
# Разворачивает архив из pack.sh на машине без интернета.
# Использование: subsidy/infra/ollama/load.sh /path/to/bundle
set -euo pipefail
IN="${1:?укажите каталог с архивом}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
VOLUME="$(grep -E '^volume=' "$IN/MODELS.txt" | cut -d= -f2)"
echo "образ ← $IN/ollama-image.tar"
docker load -i "$IN/ollama-image.tar"
echo "веса → том $VOLUME"
docker volume create "$VOLUME" >/dev/null
docker run --rm -v "$VOLUME:/models" -v "$IN:/in" alpine tar xf /in/ollama-models.tar -C /models
echo "готово. Дальше: cd subsidy/infra && docker compose --profile llm-local up -d ollama"
