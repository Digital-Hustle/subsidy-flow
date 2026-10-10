# AGENTS.md — subsidy-flow

Код приложения — в `subsidy/`. Перед любой задачей прочитай `subsidy/AGENTS.md` (карта, владельцы, порядок работы) и `subsidy/rules.md` (красные линии), затем `AGENTS.md` модуля, с которым работаешь.

- Скиллы — `.agents/skills/` (`.claude/skills` — симлинк): процесс кейса — `subsidy-dev`, бэкенд — `backend-spring`, фронт — `frontend-fsd`; перечень — `subsidy/docs/AGENT_SKILLS.md`.
- MCP: `.mcp.json` (Claude Code), `.codex/config.toml` (Codex), правила Cursor — `.cursor/rules/`.
- Команды запускай из `subsidy/` (`scripts/verify.sh`, `scripts/guard.sh`, `scripts/ci-local.sh`).
- Секреты в git не кладём; проверки не отключаем.
