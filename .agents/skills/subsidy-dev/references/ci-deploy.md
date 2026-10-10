# CI и деплой (подробно: docs/DEPLOY.md)

- CI: `.github/workflows/subsidy-ci.yml` (в корне репо) — jobs changes / backend (матрица сервисов: Liquibase → jOOQ → `check`, be-check, exportOpenApi diff) / ocr (uv, ruff, mypy, pytest) / eval (синтетика) / frontend (npm, fsd-check) / security / smoke.
- Упал CI → `gh pr checks <n>` → `gh run view <id> --log-failed` → воспроизведи локально через `scripts/verify.sh <part>` → чини в той же ветке новым коммитом.
- Не отключай job и не добавляй `continue-on-error` и `@Disabled`, чтобы обойти падение.
- Gitleaks нашёл секрет → не коммить «удаление» поверх: сообщи человеку немедленно. Ключ надо ротировать, историю ветки — переписать (`git rebase -i`) до мержа.
- Деплой: автоматически из `main` (`subsidy-deploy.yml`). Руками: `gh workflow run subsidy-deploy.yml -f ref=<tag>`.
- Откат: `gh workflow run subsidy-deploy.yml -f ref=subs-demo-N`.
- Локально весь стенд: `cd infra && docker compose --profile llm-local up -d --build`; логи: `docker compose logs -f docproc ocr`; трейсы: Jaeger UI (`docker compose port jaeger 16686` только локально).
- Пересобрать один сервис: `docker compose up -d --build docproc`.
- Смоук: `scripts/smoke.sh https://<host>`.
