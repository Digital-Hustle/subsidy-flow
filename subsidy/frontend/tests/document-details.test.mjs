import { test } from "node:test";
import assert from "node:assert/strict";
import { documentParties, signatureIndicators, presenceText } from "../src/entities/application/model/document-details.ts";
const field = (name, value) => ({ name, value, extractedValue: value, raw: null, confidence: null, checks: [] });

test("guarantor and beneficiary remain distinct, even when one is absent", () => {
  const doc = { type: "GUARANTEE_LETTER", fields: [field("guarantorName", "Гарант"), field("beneficiaryName", "Заявитель")] };
  const parties = documentParties(doc);
  assert.equal(parties[0].field.value, "Заявитель");
  assert.equal(parties[2].field.value, "Гарант");
  assert.equal(documentParties({ ...doc, fields: [field("guarantorName", "Гарант")] })[0].field, undefined);
});
test("false is distinct from absent and nonboolean extraction", () => {
  assert.equal(presenceText(true), "Обнаружено");
  assert.equal(presenceText(false), "Не обнаружено");
  for (const value of [null, undefined, "false", "есть", 1]) assert.equal(presenceText(value), "Не определено");
});
test("electronic signature comes from file structure and does not become cryptographic validation", () => {
  const doc = { fields: [field("signaturePresent", true), field("stampPresent", false), field("electronicSignaturePresent", true)] };
  assert.deepEqual(signatureIndicators(doc).map((i) => i.value), [true, false, undefined]);
  assert.equal(signatureIndicators(doc, { present: false, status: "ABSENT", cryptographicallyVerified: false, reason: null })[2].value, false);
});
