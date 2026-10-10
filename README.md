# subsidy-flow

Автоматическая проверка пакета документов на субсидию: заявитель загружает пакет (документы PDF), сервер в фоне распознаёт текст, определяет вид каждого документа по содержимому, извлекает поля и прогоняет правила программы поддержки, а специалист сверяет результат с оригиналами, правит и формирует протокол проверки. Хакатон «СберБизнесВайб» 2026, кейс 9.1.3 («Агентство инноваций»).

| Папка | Что |
|---|---|
| [`subsidy/`](subsidy/) | приложение: сервисы Java/Spring и Python (OCR), фронт React (FSD), инфраструктура, eval, документация — начать с [`subsidy/README.md`](subsidy/README.md) |
| [`edge/`](edge/) | edge-прокси сервера (TLS, маршрут по хосту) |
| [`mail/`](mail/) | почтовый узел сервера (postfix → SMTP провайдера), см. [`MAIL.md`](MAIL.md) |
| [`.agents/skills/`](.agents/skills/) | скиллы для ИИ-ассистентов (`.claude/skills` — симлинк), перечень — [`subsidy/docs/AGENT_SKILLS.md`](subsidy/docs/AGENT_SKILLS.md) |

## Быстрый старт
```bash
git clone https://github.com/Digital-Hustle/subsidy-flow && cd subsidy-flow/subsidy
scripts/gen-env.sh
cd infra && docker compose up -d --build
```
Подробности, проверки и ограничения — [`subsidy/README.md`](subsidy/README.md), развёртывание — [`subsidy/docs/DEPLOY.md`](subsidy/docs/DEPLOY.md).

## Правила
- Никаких секретов в git: `.env` только локально и на сервере (`subsidy/scripts/gen-env.sh`).
- Работа через PR: ветка на задачу, зелёный CI (`.github/workflows/subsidy-ci.yml`), ревью владельца модуля (`.github/CODEOWNERS`).
- Правила разработки и красные линии — [`subsidy/rules.md`](subsidy/rules.md), процесс — [`subsidy/docs/WORKFLOW.md`](subsidy/docs/WORKFLOW.md).
