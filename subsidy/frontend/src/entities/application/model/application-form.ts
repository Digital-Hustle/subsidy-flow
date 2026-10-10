import type { ApplicationData } from "./types";

export const APPLICATION_LABELS = {
  programCode: "Программа субсидии", orgInn: "ИНН организации", orgName: "Наименование организации", ogrn: "ОГРН / ОГРНИП",
  address: "Адрес организации", addressPurpose: "Тип адреса", project: "Название проекта", requestedAmount: "Запрашиваемая сумма, ₽",
  totalCost: "Общие расходы проекта, ₽", ownFunds: "Собственные средства, ₽", applicationDate: "Дата заявки",
  signerName: "Подписант", signerPosition: "Должность подписанта", signerAuthority: "Основание полномочий",
  cadastralNumber: "Кадастровый номер", area: "Площадь участка, м²", purpose: "Назначение участка", propertyAddress: "Адрес участка", rightHolder: "Правообладатель", rightType: "Вид права",
} as const;
export type ApplicationDraft = Record<keyof typeof APPLICATION_LABELS, string>;
export const needsProperty = (program: string) => program === "BEACH" || program === "MODERNIZATION";
export const applicationDraft = (programCode = "", application?: Partial<ApplicationData> | null, inn = ""): ApplicationDraft => ({
  programCode, orgInn: application?.orgInn ?? inn, orgName: application?.orgName ?? "", ogrn: application?.ogrn ?? "",
  address: application?.address?.value ?? "", addressPurpose: application?.address?.purpose ?? "LEGAL",
  project: application?.project ?? "", requestedAmount: application?.requestedAmount ?? "", totalCost: application?.totalCost ?? "", ownFunds: application?.ownFunds ?? "",
  applicationDate: application?.applicationDate ?? "", signerName: application?.signatory?.fullName ?? "", signerPosition: application?.signatory?.position ?? "", signerAuthority: application?.signatory?.authority ?? "",
  cadastralNumber: application?.property?.cadastralNumber ?? "", area: application?.property?.area ?? "", purpose: application?.property?.purpose ?? "", propertyAddress: application?.property?.address ?? "", rightHolder: application?.property?.rightHolder ?? "", rightType: application?.property?.rightType ?? "",
});
const text = (s: string) => s.trim() || null;
const decimal = (s: string) => text(s)?.replace(",", ".") ?? null;
export const applicationPayload = (d: ApplicationDraft): ApplicationData => ({
  orgInn: d.orgInn.trim(), orgName: text(d.orgName), ogrn: text(d.ogrn),
  address: text(d.address) ? { value: text(d.address), purpose: text(d.addressPurpose) } : null,
  project: text(d.project), requestedAmount: decimal(d.requestedAmount), totalCost: decimal(d.totalCost), ownFunds: decimal(d.ownFunds), applicationDate: text(d.applicationDate),
  signatory: { fullName: text(d.signerName), position: text(d.signerPosition), authority: text(d.signerAuthority) },
  property: needsProperty(d.programCode) ? { cadastralNumber: text(d.cadastralNumber), area: decimal(d.area), purpose: text(d.purpose), address: text(d.propertyAddress), rightHolder: text(d.rightHolder), rightType: text(d.rightType) } : null,
});
export const canSubmitApplication = (d: ApplicationDraft) => /^\d{10}$|^\d{12}$/.test(d.orgInn) && !!d.programCode;

/** Серверные ошибки бывают errors:["application.field: message"] или {field,message}. */
export const applicationErrors = (params: Record<string, unknown>, fallback: string): Partial<Record<keyof ApplicationDraft, string>> => {
  const map: Record<string, keyof ApplicationDraft> = { "address.value": "address", "address.purpose": "addressPurpose", "signatory.fullName": "signerName", "signatory.position": "signerPosition", "signatory.authority": "signerAuthority", "property.address": "propertyAddress" };
  const result: Partial<Record<keyof ApplicationDraft, string>> = {};
  const entries = Array.isArray(params.errors) ? params.errors : params.field ? [{ field: params.field, message: fallback }] : [];
  for (const error of entries) {
    let field: unknown, message: unknown;
    if (typeof error === "string") { const i = error.indexOf(":"); field = i < 0 ? error : error.slice(0, i); message = i < 0 ? fallback : error.slice(i + 1).trim(); }
    else if (error && typeof error === "object") { field = error.field; message = error.message ?? error.detail ?? fallback; }
    if (typeof field !== "string" || typeof message !== "string") continue;
    const key = field.replace(/^application\./, "");
    const mapped = map[key] ?? key.replace(/^property\./, "");
    if (mapped in APPLICATION_LABELS) result[mapped as keyof ApplicationDraft] = message;
  }
  return result;
};

/** Не выдаём успешное сохранение, если старый API молча отбросил заполненные поля. */
export const unsavedApplicationFields = (expected: ApplicationData, actual?: ApplicationData | null): string[] => {
  const missing: string[] = [];
  const visit = (want: unknown, got: unknown, path: string) => {
    if (want === null || want === undefined || want === "") {
      if (got !== null && got !== undefined && got !== "") missing.push(path);
      return;
    }
    if (typeof want === "object") {
      for (const [key, value] of Object.entries(want)) visit(value, got && typeof got === "object" ? (got as Record<string, unknown>)[key] : undefined, path ? `${path}.${key}` : key);
    } else {
      // Сравнение только представлений, без арифметики и вердиктов по документам.
      const normalized = (value: unknown) => {
        if (typeof value !== "string") return value;
        if (!/(requestedAmount|totalCost|ownFunds|area)$/.test(path)) return value.trim();
        const [integer, fraction = ""] = value.trim().replace(",", ".").split(".");
        return `${integer.replace(/^0+(?=\d)/, "")}.${fraction.replace(/0+$/, "")}`;
      };
      if (normalized(want) !== normalized(got)) missing.push(path);
    }
  };
  visit(expected, actual, "");
  return missing;
};

/** Пустая анкета не мешает создать пакет по существующему минимальному контракту. */
export const hasApplicationDetails = (a: ApplicationData) => {
  const filled = (v: unknown): boolean => v !== null && v !== undefined && v !== "" && (typeof v !== "object" || Object.values(v).some(filled));
  return Object.entries(a).some(([key, value]) => key !== "orgInn" && filled(value));
};
