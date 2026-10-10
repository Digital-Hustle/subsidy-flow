#!/usr/bin/env bash
# Смоук стенда «Субсидии». Использование: scripts/smoke.sh https://host
# Со сквозным прогоном: EVAL_PASSWORD=<пароль specialist из infra/.env> scripts/smoke.sh https://host
#   — генерирует синтетический пакет (eval/synthetic), грузит его через публичный API и сверяет протокол с разметкой (расхождения печатаются, падает — только если протокол не получен).
set -euo pipefail
H="${1:-https://localhost}"; K="${KEYCLOAK_URL:-$H/auth}"
GW_HEALTH="${GATEWAY_HEALTH_URL:-$H/actuator/health}"
ok() { echo "✔ $1"; } ; bad() { echo "✖ $1"; exit 1; }
code_of() { curl -sk -o /dev/null -w '%{http_code}' "$@"; }

curl -fsSk "$GW_HEALTH" | grep -q '"UP"' && ok "gateway health" || bad "gateway health ($GW_HEALTH)"
curl -fsSk "$K/realms/subsidy/.well-known/openid-configuration" >/dev/null && ok "keycloak realm subsidy" || bad "keycloak"
curl -fsSk "$H/specialist" | grep -qi '<div id="root"' && ok "frontend /specialist" || bad "frontend /specialist"

# Рабочее место закрыто: каждый сервис без токена — 401
for svc in package docproc rules assistant; do
  c=$(code_of "$H/$svc/api/v1/ping"); [ "$c" = 401 ] && ok "$svc: без токена 401" || bad "$svc: без токена вернул $c"
done
c=$(code_of "$H/package/api/v1/files/00000000-0000-0000-0000-000000000000/content")
[[ "$c" = 401 || "$c" = 403 ]] && ok "файл по прямой ссылке без входа: $c" || bad "файл по прямой ссылке без входа: $c"

# Д11: результат проверки нельзя прислать с клиента — эндпоинта записи результата нет
c=$(code_of -X POST "$H/rules/api/v1/packages/00000000-0000-0000-0000-000000000000/result" -H 'Content-Type: application/json' -d '{"passed":true}')
[[ "$c" = 401 || "$c" = 404 || "$c" = 405 ]] && ok "запись результата с клиента невозможна ($c)" || bad "POST результата вернул $c"

for p in /internal/package/files /rules/internal/evaluate /ocr/v1/extract; do
  c=$(code_of "$H$p"); [[ "$c" = 401 || "$c" = 403 || "$c" = 404 ]] && ok "$p закрыт снаружи ($c)" || bad "$p снаружи: $c"
done
if [ -n "${EVAL_PASSWORD:-}" ]; then
  command -v uv >/dev/null || bad "для сквозного прогона нужен uv (https://docs.astral.sh/uv/)"
  D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
  cd "$(dirname "$0")/../eval"
  uv run -q python -m synthetic.gen --out "$D/pkg" --seed "${SMOKE_SEED:-1}" --merged >/dev/null && ok "синтетический пакет: $(ls "$D/pkg" | grep -c pdf) PDF"
  uv run -q python run.py --api "$H" --dir "$D/pkg" --user "${EVAL_USER:-specialist}" --timeout "${SMOKE_TIMEOUT:-1800}" --report "$D/report.md" \
    && ok "сквозной прогон: протокол получен ($(grep -o 'Расхождений: [0-9]*' "$D/report.md"))" || bad "сквозной прогон: протокол не получен"
else
  echo "ℹ сквозной прогон пропущен: задайте EVAL_PASSWORD (пароль specialist) — или пройдите маршрут из docs/DEMO.md руками"
fi
