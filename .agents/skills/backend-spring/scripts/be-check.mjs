#!/usr/bin/env node
// Статические правила скилла backend-spring для Gradle-монорепы сервисов.
// node be-check.mjs <services-dir> [--warn-only]
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.argv[2] ?? ".");
const warnOnly = process.argv.includes("--warn-only");
// корень пакетов: ru.<case>, где <case> из rootProject.name = "<case>-services" (proxodnaya | documents | subsidy)
const settingsFile = path.join(root, "settings.gradle.kts");
const caseName = (fs.existsSync(settingsFile)
  && (fs.readFileSync(settingsFile, "utf8").match(/rootProject\.name\s*=\s*"([a-z]+)-services"/) || [])[1]) || "proxodnaya";
const ROOT_PKG = `ru.${caseName}`;
const issues = [];
const add = (rule, file, msg) => issues.push({ rule, file: path.relative(root, file), msg });

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (["build", ".gradle", "node_modules", ".venv", "generated"].includes(e.name)) continue;
    const p = path.join(dir, e.name);
    e.isDirectory() ? walk(p, out) : out.push(p);
  }
  return out;
}

const services = fs.readdirSync(root, { withFileTypes: true })
  .filter((d) => d.isDirectory() && fs.existsSync(path.join(root, d.name, "build.gradle.kts")) && d.name !== "build-logic")
  .map((d) => d.name);

for (const svc of services) {
  const dir = path.join(root, svc);
  const files = walk(dir);
  let pkgRoot = `${ROOT_PKG}.${svc}`;
  if (process.argv.includes("--infer-pkg")) {   // для чужих репозиториев: корень = пакет *Application
    const app = files.find((f) => /src\/main\/java\/.*Application\.java$/.test(f));
    if (app) pkgRoot = (fs.readFileSync(app, "utf8").match(/^package\s+([\w.]+);/m) || [])[1] ?? pkgRoot;
  }
  for (const f of files) {
    const rel = path.relative(dir, f).split(path.sep).join("/");
    const isMain = rel.startsWith("src/main/java/");
    const isTest = rel.startsWith("src/test/java/");

    // build-файлы: версии только в catalog
    if (rel === "build.gradle.kts") {
      const s = fs.readFileSync(f, "utf8");
      if (/"[\w.-]+:[\w.-]+:\d[^"]*"/.test(s)) add("version-in-build", f, "версия зависимости в build.gradle.kts — перенеси в gradle/libs.versions.toml");
      if (/id\("org\.springframework\.boot"\)\s*version/.test(s)) add("version-in-build", f, "версия плагина — только в build-logic");
    }

    // Liquibase
    if (rel.includes("db/changelog/changesets/")) {
      const name = path.basename(f);
      if (!/^\d{14}-[a-z0-9-]+\.sql$/.test(name)) add("changeset-name", f, "имя changeset: <yyyyMMddHHmmss>-<slug>.sql (scripts/new-migration.sh)");
      const s = fs.readFileSync(f, "utf8");
      if (!s.startsWith("--liquibase formatted sql")) add("changeset-format", f, "первая строка: --liquibase formatted sql");
      if (!/--changeset \S+:\S+/.test(s)) add("changeset-format", f, "нет --changeset author:id");
      if (!/--rollback/.test(s)) add("changeset-rollback", f, "нет --rollback");
    }

    // yml: секреты только через ${ENV}
    if (/src\/main\/resources\/application[\w-]*\.ya?ml$/.test(rel) && !rel.endsWith("application-local.yml")) {
      fs.readFileSync(f, "utf8").split("\n").forEach((line, i) => {
        const m = line.match(/^\s*(password|secret|client-secret|token|api-key|access-key|secret-key)\s*:\s*(.+)$/i);
        if (m && !/^\$\{[A-Z0-9_]+\}$/.test(m[2].trim()) && !/^\$\{spring\./.test(m[2].trim()))
          add("secret-literal", f, `строка ${i + 1}: секрет должен быть \${ENV} без значения по умолчанию`);
        if (/exposure:\s*$/.test(line) || /include:\s*["']?\*/.test(line)) {
          if (/include:\s*["']?\*/.test(line)) add("actuator-exposure", f, `строка ${i + 1}: actuator include * запрещён`);
        }
      });
    }

    if (!f.endsWith(".java") || !(isMain || isTest)) continue;
    const src = fs.readFileSync(f, "utf8");
    const pkg = (src.match(/^package\s+([\w.]+);/m) || [])[1] ?? "";
    if (!pkg.startsWith(pkgRoot)) { add("package-root", f, `пакет должен начинаться с ${pkgRoot}`); continue; }
    const sub = pkg.slice(pkgRoot.length + 1);
    const imports = [...src.matchAll(/^import\s+(?:static\s+)?([\w.]+)(?:\.\*)?;/gm)].map((m) => m[1]);
    const cls = path.basename(f, ".java");
    const code = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");

    if (/\b(TODO|FIXME)\b/.test(src)) add("no-todo", f, "TODO/FIXME запрещены — заведи issue");
    if (!isMain) continue;

    // общие запреты
    if (/System\.(out|err)\.print/.test(code)) add("no-sysout", f, "System.out/err — используй логгер");
    if (/@Autowired\s+(private|protected|public)?\s*[\w<>, ?]+\s+\w+\s*;/.test(code)) add("field-injection", f, "field injection — только конструктор (@RequiredArgsConstructor)");
    if (/\b(LocalDateTime|LocalDate|OffsetDateTime|ZonedDateTime|Instant)\.now\(\s*\)/.test(code)) add("clock", f, "now() без Clock — внедри java.time.Clock");
    if (/\bThreadLocal\b/.test(code)) add("thread-local", f, "ThreadLocal для контекста запроса запрещён — CurrentUser из JWT");
    if (/@Data\b/.test(code) && /import lombok\.Data;/.test(src)) add("lombok-data", f, "@Data запрещён — record или @Getter/@Builder");
    if (/@SneakyThrows/.test(code)) add("sneaky-throws", f, "@SneakyThrows запрещён");
    if (imports.includes("org.springframework.kafka.core.KafkaTemplate") && !sub.includes("messaging.outbox"))
      add("kafka-template", f, "KafkaTemplate только в messaging.outbox — публикуй через Outbox.add");
    if (/\bgetHeader\("X-User-Id"\)|"X-User-Id"/.test(code)) add("trust-header", f, "не доверяем X-User-Id — пользователь из JWT");
    if (/ResponseEntity</.test(code) && !/^(api\.)?controller|^exception/.test(sub)) add("response-entity", f, "ResponseEntity только на границе контроллера");

    // контроллеры
    if (sub === "api.controller" && !/\binterface\s+\w+/.test(code)) add("controller-interface", f, "в api.controller — только интерфейсы, реализация в api.controller.impl");
    if (sub === "api.controller.impl") {
      if (!/@RestController/.test(code)) add("controller-impl", f, "реализация контроллера — @RestController");
      if (imports.some((i) => /\.repository\.|^org\.jooq/.test(i))) add("thin-controller", f, "контроллер не обращается к репозиториям/jOOQ");
      if (/@Transactional/.test(code)) add("thin-controller", f, "транзакции не в контроллере");
    }
    if (sub.startsWith("api.controller") && /(Map<String,\s*Object>|HashMap)/.test(code)) add("adhoc-map", f, "ad-hoc Map в ответе — используй Rs/ProblemDetail");
    if (sub === "api.dto.request" && !cls.endsWith("Rq")) add("dto-naming", f, "request DTO должен оканчиваться на Rq");
    if (sub === "api.dto.response" && !cls.endsWith("Rs")) add("dto-naming", f, "response DTO должен оканчиваться на Rs");
    if (/^api\.(controller|dto)/.test(sub) && imports.some((i) => /\.features\.\w+\.(command|query)\.model\./.test(i) || /\.jooq\./.test(i)))
      if (sub.startsWith("api.dto")) add("leak-model", f, "DTO не ссылается на модели фич и jOOQ-records");

    // фичи
    const fm = sub.match(/^features\.(\w+)(?:\.(.+))?$/);
    if (fm) {
      const [, feature, inner = ""] = fm;
      for (const imp of imports) {
        const t = imp.match(new RegExp(`^${pkgRoot.replace(/\./g, "\\.")}\\.features\\.(\\w+)\\.(\\w+)`));
        if (t && t[1] !== feature && !["api", "spi"].includes(t[2]))
          add("cross-feature", f, `фича ${feature} импортирует ${t[1]}.${t[2]} — только через ${t[1]}.api или свой spi`);
        if (imp.startsWith(`${pkgRoot}.api.`)) add("feature-to-http", f, "фича не зависит от HTTP-слоя api.*");
      }
      if (inner.startsWith("query") && imports.some((i) => i.includes(`.features.${feature}.command.`)))
        add("cqrs", f, "query не вызывает command");
      if (inner.startsWith("application") && imports.some((i) => /\.repository\.|^org\.jooq/.test(i)))
        add("application-repo", f, "application-сервис не ходит в репозитории — через command/query");
      if (/(^|\.)(service|application)(\.|$)/.test(inner) && imports.some((i) => /^org\.springframework\.web\.client|^java\.net\.http|^okhttp3|^io\.minio/.test(i)))
        add("gateway", f, "внешние HTTP/SDK — только через ...Gateway в пакете gateway");
      if (inner.startsWith("command.service") || inner.startsWith("application.service")) {
        if (/\b(restClient|webClient|faceGateway|llmGateway)\b[\s\S]{0,400}/.test(code) && /@Transactional/.test(code))
          add("tx-external", f, "внешний вызов рядом с @Transactional — проверь, что он вне транзакции (warn)");
      }
    }
    if (sub.startsWith("shared") && imports.some((i) => i.startsWith(`${pkgRoot}.features`) || i.startsWith(`${pkgRoot}.api`)))
      add("shared-independent", f, "shared не зависит от features/api");
  }
}

const byRule = {};
for (const i of issues) (byRule[i.rule] ??= []).push(i);
for (const [rule, list] of Object.entries(byRule)) {
  console.log(`\n[${rule}] ${list.length}`);
  for (const i of list) console.log(`  ${i.file}: ${i.msg}`);
}
console.log(`\nbe-check: ${services.length} модул(ей), нарушений: ${issues.length}`);
process.exit(issues.length && !warnOnly ? 1 : 0);
