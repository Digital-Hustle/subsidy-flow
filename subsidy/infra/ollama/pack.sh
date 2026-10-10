#!/usr/bin/env bash
# Складывает образ Ollama и скачанные веса в каталог-архив для машины без интернета.
# Использование: subsidy/infra/ollama/pack.sh /path/to/bundle
set -euo pipefail
OUT="${1:?укажите каталог для архива}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
[ -f .env ] && set -a && . ./.env && set +a || true
IMAGE="$(grep -E '^\s+image: ollama/ollama' docker-compose.yml | head -1 | awk '{print $2}')"
VOLUME="$(docker compose config --format json | python3 -c 'import json,sys; print(next(iter(json.load(sys.stdin)["volumes"])))' 2>/dev/null || echo ollama)"
PROJECT="$(docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')"
mkdir -p "$OUT"
echo "образ $IMAGE → $OUT/ollama-image.tar"
docker pull "$IMAGE"
docker save "$IMAGE" -o "$OUT/ollama-image.tar"
echo "веса из тома ${PROJECT}_${VOLUME} → $OUT/ollama-models.tar"
docker run --rm -v "${PROJECT}_${VOLUME}:/models" -v "$OUT:/out" alpine \
  tar cf /out/ollama-models.tar -C /models .
{
  echo "image=$IMAGE"
  echo "volume=${PROJECT}_${VOLUME}"
  echo "LLM_MODEL=${LLM_MODEL:-qwen3:8b}"
  echo "LLM_VISION_MODEL=${LLM_VISION_MODEL:-qwen2.5vl:7b}"
} > "$OUT/MODELS.txt"
echo "готово: $(du -sh "$OUT" | cut -f1) в $OUT"
