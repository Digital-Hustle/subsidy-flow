import type { DocumentCard, ElectronicSignature, FieldCard } from "./types";

export const INDICATOR_FIELDS = ["signaturePresent", "stampPresent", "electronicSignaturePresent"] as const;
export const presenceText = (value: unknown) => value === true ? "Обнаружено" : value === false ? "Не обнаружено" : "Не определено";

/** Роли сторон — только из схемы извлечения, без подстановки гаранта вместо заявителя. */
export const documentParties = (doc: DocumentCard): { label: string; field?: FieldCard }[] => {
  const find = (name: string) => doc.fields?.find((f) => f.name === name);
  return [
    { label: "Заявитель / получатель гарантии", field: doc.type === "GUARANTEE_LETTER" ? find("beneficiaryName") : find("orgName") },
    { label: "ИНН заявителя", field: doc.type === "GUARANTEE_LETTER" ? find("beneficiaryInn") : find("inn") },
    { label: "Организация-гарант", field: find("guarantorName") },
    { label: "ИНН гаранта", field: find("guarantorInn") },
    { label: "Правообладатель", field: find("rightHolder") },
    { label: "Арендатор", field: find("tenant") },
  ];
};

/** Для ЭП источник package — структура файла. Текст подписи на скане её не заменяет. */
export const signatureIndicators = (doc: DocumentCard, electronic?: ElectronicSignature | null) => {
  const find = (name: string) => doc.fields?.find((f) => f.name === name);
  const signature = find("signaturePresent"), stamp = find("stampPresent");
  return [
    { label: "Подпись на документе", value: signature?.extractedValue, field: signature, reason: null },
    { label: "Печать", value: stamp?.extractedValue, field: stamp, reason: null },
    { label: "Электронная подпись файла", value: electronic?.present, field: undefined, reason: electronic?.reason ?? null },
  ];
};
