// Типы API из contracts/openapi/*.json → src/shared/api/schema/<svc>.ts (openapi-typescript).
// Контракты появляются вместе с сервисами; пока их нет — генерировать нечего.
import { readdirSync, existsSync, mkdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join, basename } from "node:path";

const dir = join(import.meta.dirname, "../../contracts/openapi");
const out = join(import.meta.dirname, "../src/shared/api/schema");
const files = existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith(".json")) : [];
if (!files.length) { console.log("gen:api — контрактов в contracts/openapi нет, пропускаю"); process.exit(0); }
mkdirSync(out, { recursive: true });
for (const f of files) {
  const target = join(out, `${basename(f, ".json")}.ts`);
  execFileSync("npx", ["openapi-typescript", join(dir, f), "-o", target], { stdio: "inherit" });
}
