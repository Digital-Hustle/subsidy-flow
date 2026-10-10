#!/usr/bin/env bash
# Сканер «красных линий» из rules.md §1. FAIL = нельзя открывать PR. WARN = посмотри глазами.
set -uo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE"
fail=0; warn=0
F() { echo "✖ FAIL: $*"; fail=1; }
W() { echo "⚠ WARN: $*"; warn=1; }
g() { git grep -nIE "$@" 2>/dev/null; }   # только отслеживаемые и проиндексированные файлы

# 1. Секреты
git ls-files "$(git rev-parse --show-toplevel)" | grep -E '(^|/)\.env$|\.pem$|id_rsa|\.p12$|\.jks$' && F "в git лежат файлы секретов (.env/ключи)"
if command -v gitleaks >/dev/null; then
  gitleaks detect --no-banner --redact -v >/dev/null 2>&1 || F "gitleaks нашёл секреты: запусти 'gitleaks detect -v'"
else
  g '(api[_-]?key|secret|password|token)\s*[:=]\s*["'"'"'][A-Za-z0-9/+_\-]{16,}' -- ':!*.md' ':!*.example' ':!*test*' && W "похоже на захардкоженный секрет (установи gitleaks для точной проверки)"
fi
# Реальные документы людей (ПДн) и открытый набор организаторов в git не кладём
git ls-files | grep -iE '\.(pdf|jpe?g|png|heic|tiff?|docx?)$' | grep -vE '^frontend/(public|src)/.*(icon|logo|illustration)' | grep -vE '^eval/synthetic/' \
  && F "в git лежат документы/сканы — ПДн и открытый набор храним вне репозитория (eval/README.md)"

# 2. Подгонка под тестовые файлы (СН)
g -i '(open[-_]?set|closed[-_]?set|sample[-_]?\d+|test[-_]?\d+)\.(pdf|jpe?g|png)' -- 'services/*/src/main/**' 'services/*/src/**/*.py' 'services/**/prompts/**' \
  && F "имя тестового файла в боевом коде/промптах — подгонка под набор"
g 'sha256|md5' -- 'services/*/src/main/**/prompts/**' 'services/**/prompts/**' && W "хэши в промптах — не узнаём ли файлы по хэшу?"

# 3. Тип — из содержимого, правила — на сервере (Д9, Д11, СН)
g 'getOriginalFilename\(\)|originalFilename|original_filename|uploadOrder|upload_order|\.extension\(' -- 'services/docproc/**' 'services/ocr/**' 'services/rules/**' ':!*.md' \
  && F "имя/порядок/расширение файла попали в определение типа или правила — только содержимое"
g -i "(passed|status|verdict)\s*[:=]\s*['\"](PASSED|OK|VALID|COMPLETE)['\"]" -- 'frontend/src/**' ':!*.test.*' ':!**/mocks/**' \
  && F "фронтенд сам выставляет результат проверки — правила и комплектность считает только сервер"
g '\.reduce\([^)]*(amount|sum|total)' -- 'frontend/src/**' ':!*.test.*' && W "суммирование денег на фронте — арифметика только в rules (Д11); на клиенте только отображение"
g -i '(mock|stub|fake|canned|hardcoded)' -- 'services/assistant/src/main/**' 'services/rules/src/main/**' && F "mock/stub/fake в боевом коде помощника/правил"
g "return\s+\"[^\"]{40,}\"" -- 'services/assistant/src/main/**' && W "длинная строковая константа в ответе помощника — не заготовка ли?"
g '\b(double|float|Double|Float)\b' -- 'services/rules/src/main/**' && W "double/float в rules — деньги только BigDecimal"

# 4. Хранилище только PostgreSQL
g 'h2database|sqlite|hsqldb' -- 'services/**/build.gradle.kts' 'services/gradle/libs.versions.toml' 'services/*/pyproject.toml' && F "встраиваемая БД в зависимостях — данные только в PostgreSQL"
g 'static\s+(final\s+)?(Map|List|Set)<' -- 'services/*/src/main/**' ':!services/libs/**' && W "статическая коллекция в main-коде — не используется ли как хранилище?"
g 'new (FileWriter|FileOutputStream)\(|Files\.write' -- 'services/*/src/main/**' && W "запись в файл — данные и файлы должны быть в БД"
g '(LinkedBlockingQueue|ArrayBlockingQueue|ConcurrentLinkedQueue)<' -- 'services/docproc/src/main/**' && F "очередь в памяти в docproc — задания разбора только в таблице jobs (rules.md §1)"

# 5. Слой модели изолирован и вызывается по-настоящему
g -i '(mock|stub|fake|canned|hardcoded)' -- 'services/libs/llm/src/main/**' 'services/docproc/src/main/**' ':!**/test/**' \
  && F "mock/stub/fake в боевом коде слоя модели/разбора"
g 'import (com\.openai|dev\.langchain4j|io\.github\.ollama4j|chat\.giga|ai\.djl)' -- 'services/*/src/main/**' ':!services/libs/llm/**' \
  && F "SDK провайдера модели вне services/libs/llm (Д6: изоляция слоя модели)"
g '(ollama|gigachat|api\.openai|deepseek|dashscope)' -- 'services/*/src/main/java/**' ':!services/libs/llm/**' ':!**/config/**' && W "имя провайдера модели вне libs/llm — провайдер задаётся только настройкой"

# 6. Фронтенд
g '(const|let)\s+\w*(mock|fake|demo)\w*\s*=\s*\[' -- 'frontend/src/**' ':!*.test.*' ':!*.stories.*' ':!**/mocks/**' && F "моковые данные во фронтенде"
g 'from ["'"'"']msw' -- 'frontend/src/**' ':!**/mocks/**' ':!src/main.tsx' && F "MSW вне src/mocks — моки только в dev"
grep -qs 'USE_MOCKS' frontend/src/main.tsx && ! grep -qs 'import.meta.env.DEV' frontend/src/main.tsx && F "моки включаются не только в dev"
g -i 'localStorage\.setItem\([^)]*(token|jwt|key)' -- 'frontend/**' && F "токены/ключи в localStorage"

# 7. Обход проверок, время, лицензии
g '@Disabled|\.skip\(|xit\(' -- 'services/*/src/test/**' 'services/*/tests/**' 'frontend/**/*.test.*' && W "отключённые тесты"
g 'continue-on-error:\s*true' -- "$(git rev-parse --show-toplevel)/.github" && F "continue-on-error в CI"
g 'permitAll\(\)' -- 'services/*/src/main/**' && W "permitAll — убедись, что это только health/openapi"
g 'Instant\.now\(\)|LocalDateTime\.now\(\)|OffsetDateTime\.now\(\)|LocalDate\.now\(\)' -- 'services/*/src/main/**' && W "время без Clock — используй внедрённый Clock"
g -i '\b(minio|qdrant|pymupdf|fitz|pdf2image|poppler|ghostscript|itext|itextpdf)\b' -- 'services/**/build.gradle.kts' 'services/gradle/libs.versions.toml' 'services/*/pyproject.toml' 'services/*/Dockerfile' 'infra/docker-compose*.yml' \
  && F "запрещённая лицензия (AGPL/GPL: MinIO, PyMuPDF, poppler/pdf2image, ghostscript, iText) — п. 5.8.4"

# 8. Микросервисные границы
g 'kafkaTemplate\.send\(' -- 'services/*/src/main/**' ':!services/libs/**' ':!**/messaging/outbox/**' && F "публикация события мимо outbox"
g 'X-User-Id|ThreadLocal<' -- 'services/*/src/main/**' && F "контекст пользователя из заголовка/ThreadLocal — только из JWT"
SVCS=$(for d in services/*/; do [ -f "$d/build.gradle.kts" ] || [ -f "$d/pyproject.toml" ] && basename "$d"; done 2>/dev/null | grep -vE '^(libs|build-logic|gradle|config)$')
for svc in $SVCS; do
  others=$(grep -vx "$svc" <<<"$SVCS" | paste -sd'|' -)
  [ -n "$others" ] && g "jdbc:postgresql://[^\"]*/($others)\b" -- "services/$svc/src/main/**" && F "$svc подключается к чужой БД"
done
[ -f infra/docker-compose.yml ] && awk '/^  [a-z-]+:$/{svc=$1} /^    ports:/{print svc}' infra/docker-compose.yml | grep -vE '^(caddy|ollama):$' && W "сервисы с проброшенными портами — наружу только caddy"

# 9. Структура (скиллы backend-spring / frontend-fsd)
REPO=$(git rev-parse --show-toplevel)
[ -f services/settings.gradle.kts ] && { node "$REPO/.agents/skills/backend-spring/scripts/be-check.mjs" services >/dev/null || F "be-check: нарушены правила backend-spring (запусти его без >/dev/null)"; }
GEN="$REPO/.agents/skills/frontend-fsd/scripts/gen-tokens.mjs"
# у фронта кейса свой генератор (версия песочницы: градиенты тёмной темы, утилиты bg-page/pb-nav) — проверяем им
[ -f frontend/scripts/gen-tokens.mjs ] && GEN="$CASE/frontend/scripts/gen-tokens.mjs"
if node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit(a>22||(a===22&&b>=6)?0:1)' 2>/dev/null; then
  if [ -f frontend/src/shared/config/tokens.ts ]; then
    (cd frontend && node --experimental-strip-types --no-warnings "$GEN" src/shared/config/tokens.ts --css src/app/styles/theme.css --md docs/DESIGN.md --check >/dev/null) \
      || F "дизайн-токены: theme.css/DESIGN.md устарели или контраст < 4.5 — npm run gen:tokens"
  else
    A="$REPO/.agents/skills/frontend-fsd/assets"
    node --experimental-strip-types --no-warnings "$GEN" "$A/tokens.ts" --css "$A/theme.css" --md frontend/docs/DESIGN.md --check >/dev/null \
      || F "дизайн-токены скилла: assets/theme.css или frontend/docs/DESIGN.md устарели — см. frontend/docs/DESIGN.md §0"
  fi
else W "Node < 22.6 — проверка дизайн-токенов пропущена"; fi
[ -d frontend/src ] && { node "$REPO/.agents/skills/frontend-fsd/scripts/fsd-check.mjs" frontend/src >/dev/null || F "fsd-check: нарушены правила FSD (запусти его без >/dev/null)"; }

# 10. Правка смерженных миграций
if git rev-parse --verify -q origin/main >/dev/null; then
  git diff --name-status origin/main...HEAD -- 'services/*/src/main/resources/db/changelog/changesets/' | grep -E '^(M|D|R)' \
    && F "изменена/удалена существующая миграция — создай новую"
fi

[ $fail -eq 0 ] && { [ $warn -eq 0 ] && echo "✔ guard: чисто" || echo "✔ guard: FAIL нет, проверь WARN"; }
exit $fail
