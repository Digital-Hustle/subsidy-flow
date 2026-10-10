# Скиллы и MCP для агентов — «Субсидии»

Скиллы — папки с `SKILL.md` в `../../.agents/skills/` (`.claude/skills` — симлинк). Общие с «Проходной», плюс свой процессный скилл **`subsidy-dev`**.

**Приоритет при конфликте:** `subsidy/rules.md` → `subsidy-dev` → `backend-spring` / `frontend-fsd` → сторонние скиллы. Блоки «Проходная» в сторонних скиллах читай как «наш кейс»: те же поправки (motion, токены, лицензии).

## Что брать под роль
| Роль | Обязательно | По ситуации |
|---|---|---|
| **B1** (gateway, package, common, infra, CI) | `subsidy-dev`, `backend-spring` | `springboot-security` (закрытость, Д8), `test-driven-development` (проверки приёма), `systematic-debugging`, `verification-before-completion` |
| **B2** (docproc, ocr, eval) | `subsidy-dev` (+ `references/pipeline.md`), `backend-spring` (+ `references/python-service.md`, `data-messaging.md`) | `test-driven-development` (очередь, контрольные суммы ИНН/ОГРН/БИК, сегментация), `systematic-debugging` (ошибки OCR), `verification-before-completion` (прогон eval перед «готово») |
| **B3** (rules, assistant, libs/llm) | `subsidy-dev` (+ `references/llm.md`), `backend-spring` (+ `frontend-contract.md`: SSE, курсоры, выгрузки) | `test-driven-development` (каждое правило — тест с числами, история, матрица ролей), `springboot-security` |
| **F1** (страница пакета) | `subsidy-dev` (+ `references/frontend.md`), `frontend-fsd` (§8) | `emil-design-eng`, `animate`, `shadcn`, `webapp-testing` (1440×900) |
| **F2** (очередь, протокол, shared) | `subsidy-dev`, `frontend-fsd` (§8) | `shadcn`, `frontend-design`, `design-taste-frontend`, `improve-animations`, `webapp-testing` (1440×900) |
| Все | — | `find-skills`, `verification-before-completion` |

## Установленные скиллы
| Скилл | Источник | Лицензия | Зачем |
|---|---|---|---|
| `subsidy-dev`, `backend-spring`, `frontend-fsd` | наши | — | процесс кейса, бэкенд, фронт (FSD + дизайн-система + Motion) |
| `shadcn` | shadcn-ui/ui | MIT | CLI, реестры, `add/view/search`, композиция компонентов |
| `frontend-design` | anthropics/skills | Apache-2.0 | визуальный уровень без «дефолтного» вида |
| `design-taste-frontend` | leonxlnx/taste-skill | MIT | анти-шаблонный дизайн входа, дашборда, демо |
| `emil-design-eng`, `animate`, `review-animations`, `improve-animations`, `find-animation-opportunities` | emilkowalski/skills | MIT | качество движения |
| `webapp-testing` | anthropics/skills | Apache-2.0 | Playwright-проверка UI локально |
| `test-driven-development`, `systematic-debugging`, `verification-before-completion` | obra/superpowers | MIT | red-green, отладка, «готово» только после проверки |
| `springboot-security` | affaan-m/ecc | MIT | чек-лист Spring Security (поверх SECURITY.md) |
| `find-skills` | vercel-labs/skills | MIT | поиск и установка новых скиллов |

Источник, коммит и лицензия стороннего скилла — в его `UPSTREAM.md`.

## MCP
`shadcn` и `magicui` из `.mcp.json` / `.cursor/mcp.json` / `.codex/config.toml`. Конфиг запускает shadcn из `subsidy/frontend` (CLI: `npx shadcn@latest add …` оттуда же), `components.json` с реестрами — шаблон `.agents/skills/frontend-fsd/assets/components.json`.

## Как добавить скилл
`npx skills find <запрос>` → `npx skills add <owner/repo@skill> -a claude-code --copy -y` → проверить лицензию → строка сюда и в `DECISIONS.md`.
