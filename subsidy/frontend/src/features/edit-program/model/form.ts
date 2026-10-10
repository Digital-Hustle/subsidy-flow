import type { Program, ProgramChecklistItem, ProgramRule } from "@/entities/protocol";

export type ChecklistKind = "REQUIRED" | "CONDITIONAL" | "ONE_OF";
export type ChecklistRow = { key: string; item: string; title: string; kind: ChecklistKind; types: string[]; hint: string; condition: string | null; single: boolean };
export type ProgramForm = { code: string; name: string; description: string; deadline: string; ownShare: string; amountMin: string; amountMax: string; rules: ProgramRule[]; checklist: ChecklistRow[]; extra: Record<string, unknown> };

/** Правило лимита суммы, которым управляют поля «от / до»; считает его rules (RANGE на BigDecimal). */
export const AMOUNT_RULE = "AMOUNT_RANGE";
const SHARE_OP = "RATIO_MIN";
const KINDS: ChecklistKind[] = ["REQUIRED", "CONDITIONAL", "ONE_OF"];
export const CODE_RE = /^[A-Z][A-Z0-9_]{1,31}$/;
const MONEY_RE = /^\d+(\.\d{1,2})?$/;
const PERCENT_RE = /^\d{1,3}([.,]\d{1,2})?$/;
const HUNDRED = 100;
const MAX_DESCRIPTION = 2000;

let seq = 0;
const nextKey = () => `row-${++seq}`;
const str = (v: unknown) => (v == null ? "" : String(v));

/** «1 000 000,50» → «1000000.50»: только формат строки, без вычислений. */
export const moneyInput = (v: string) => v.replace(/\s/g, "").replace(",", ".");

export const emptyRow = (): ChecklistRow => ({ key: nextKey(), item: "", title: "", kind: "REQUIRED", types: [], hint: "", condition: null, single: false });

const toRow = (c: ProgramChecklistItem): ChecklistRow => ({
  key: nextKey(), item: c.item, title: c.title ?? "", kind: KINDS.includes(c.kind as ChecklistKind) ? (c.kind as ChecklistKind) : "REQUIRED",
  types: c.types ?? [], hint: c.conditionText ?? "", condition: c.condition ?? null, single: !!c.single,
});

/** Доля из параметра правила («0.5000») в проценты для поля ввода — только представление. */
const percentText = (min: unknown) => {
  const s = str(min).trim();
  if (!s) return "";
  return String(Number((Number(s) * HUNDRED).toFixed(2)));
};

/** Программа → форма. base — источник правил и чек-листа («на основе»); для правки base = p. */
export const toForm = (p: Program | null, base: Program | null = p): ProgramForm => {
  const { description, deadline, rules, ...extra } = base?.params ?? {};
  const all = (rules ?? []).map((r) => ({ ...r }));
  const share = all.find((r) => r.op === SHARE_OP);
  const amount = all.find((r) => r.code === AMOUNT_RULE);
  return {
    code: p?.code ?? "", name: p?.name ?? "",
    description: p ? str(description) : "", deadline: p ? str(deadline) : "",
    ownShare: percentText(share?.min), amountMin: str(amount?.min), amountMax: str(amount?.max),
    rules: all.filter((r) => r.code !== AMOUNT_RULE), checklist: (base?.checklist ?? []).map(toRow), extra,
  };
};

export const hasShareRule = (f: ProgramForm) => f.rules.some((r) => r.op === SHARE_OP);

/** Уникальный item пункта: прежний или по первому виду документа. */
const items = (rows: ChecklistRow[]) => {
  const used = new Set<string>();
  return rows.map((r) => {
    const base = r.item || r.types[0] || "DOC";
    let item = base;
    for (let i = 2; used.has(item); i++) item = `${base}_${i}`;
    used.add(item);
    return item;
  });
};

/** Форма → тело запроса rules. Доли и лимиты — параметры правил; применяет и считает их сервер. */
export const toBody = (f: ProgramForm) => {
  const share = f.ownShare.trim().replace(",", ".");
  const rules: ProgramRule[] = f.rules.map((r) => (r.op === SHARE_OP && share ? { ...r, min: (Number(share) / HUNDRED).toFixed(4) } : r));
  const min = moneyInput(f.amountMin);
  const max = moneyInput(f.amountMax);
  if (min || max) {
    rules.push({ code: AMOUNT_RULE, title: "Запрашиваемая сумма в пределах программы", op: "RANGE", severity: "FAILED", value: ["APPLICATION.requestedAmount", "COST_JUSTIFICATION.requestedAmount"], ...(min ? { min } : {}), ...(max ? { max } : {}) });
  }
  const keys = items(f.checklist);
  return {
    code: f.code.trim(), name: f.name.trim(),
    params: { ...f.extra, description: f.description.trim() || null, deadline: f.deadline || null, rules },
    checklist: f.checklist.map((r, i) => ({ item: keys[i], title: r.title.trim(), kind: r.kind, types: r.types, condition: r.condition, conditionText: r.hint.trim() || null, single: r.single })),
  };
};

export type FormErrors = Partial<Record<"code" | "name" | "description" | "ownShare" | "amountMin" | "amountMax" | "checklist", string>> & { rows?: Record<string, string> };

/** Формат полей до отправки; содержательно (правила, виды, границы min ≤ max) проверяет rules. */
export const validate = (f: ProgramForm, creating: boolean): FormErrors => {
  const e: FormErrors = {};
  if (creating && !CODE_RE.test(f.code.trim())) e.code = "Латиница A–Z, цифры и «_», 2–32 символа, с буквы";
  if (!f.name.trim()) e.name = "Укажите название";
  if (f.description.length > MAX_DESCRIPTION) e.description = `Не длиннее ${MAX_DESCRIPTION} символов`;
  if (f.ownShare.trim() && !PERCENT_RE.test(f.ownShare.trim())) e.ownShare = "Число процентов, например 50";
  if (f.amountMin.trim() && !MONEY_RE.test(moneyInput(f.amountMin))) e.amountMin = "Сумма в рублях, например 500000";
  if (f.amountMax.trim() && !MONEY_RE.test(moneyInput(f.amountMax))) e.amountMax = "Сумма в рублях, например 3000000";
  if (!f.checklist.length) e.checklist = "Добавьте хотя бы один документ";
  const rows: Record<string, string> = {};
  for (const r of f.checklist) {
    if (!r.types.length) rows[r.key] = "Выберите вид документа";
    else if (r.kind === "ONE_OF" && r.types.length < 2) rows[r.key] = "Для «один из» выберите не меньше двух видов";
    else if (!r.title.trim()) rows[r.key] = "Укажите название для заявителя";
    else if (r.kind === "CONDITIONAL" && !r.hint.trim() && !r.condition) rows[r.key] = "Опишите условие";
  }
  if (Object.keys(rows).length) e.rows = rows;
  return e;
};

export const hasErrors = (e: FormErrors) => Object.keys(e).length > 0;
/** Вкладки формы, где есть ошибки, — чтобы подсказать, куда смотреть. */
export const errorTabs = (e: FormErrors) => {
  const tabs: ("main" | "docs" | "rules")[] = [];
  if (e.code || e.name || e.description) tabs.push("main");
  if (e.checklist || e.rows) tabs.push("docs");
  if (e.ownShare || e.amountMin || e.amountMax) tabs.push("rules");
  return tabs;
};
