#!/usr/bin/env bash
# Создаёт infra/.env из .env.example: каждое значение "..." заменяется случайным секретом. Существующий .env не трогает.
set -euo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE/infra"
[ -f .env ] && { echo ".env уже есть"; exit 1; }
while IFS= read -r line; do
  if [[ "$line" =~ ^([A-Z0-9_]+)=\.\.\.$ ]]; then
    k=${BASH_REMATCH[1]}
    case "$k" in
      *_SIGNING_KEY) v=$(openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0) ;;
      FILES_ENCRYPTION_KEY) v=$(openssl rand -base64 32) ;;
      KAFKA_CLUSTER_ID) v=$(openssl rand -base64 16 | tr '+/' '-_' | tr -d '=') ;;
      PUBLIC_HOST|KC_HOSTNAME) v=localhost ;;
      OIDC_ISSUER_URI) v=https://localhost/auth/realms/subsidy ;;
      KC_BOOTSTRAP_ADMIN_USERNAME) v=kcadmin ;;
      LLM_API_KEY|GIGACHAT_AUTH_KEY) v= ;;
      *) v=$(openssl rand -base64 24 | tr -d '/+=') ;;
    esac
    echo "$k=$v"
  else echo "$line"; fi
done < .env.example > .env
chmod 600 .env && echo "infra/.env создан"
