#!/usr/bin/env bash
# Ставит pre-commit хук: gitleaks по staged-изменениям + guard. Запустить один раз после clone.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cat > "$ROOT/.git/hooks/pre-commit" <<'H'
#!/usr/bin/env bash
if command -v gitleaks >/dev/null; then gitleaks protect --staged --no-banner --redact || exit 1
else echo "⚠ gitleaks не установлен: brew install gitleaks / скачать с GitHub releases"; fi
for g in */scripts/guard.sh; do [ -x "$g" ] && { "$g" >/dev/null || { "$g"; exit 1; }; }; done
H
chmod +x "$ROOT/.git/hooks/pre-commit"; echo "✔ pre-commit установлен"
