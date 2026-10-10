import { test } from "node:test";
import assert from "node:assert/strict";
import { applicationDraft, applicationPayload, applicationErrors, canSubmitApplication, unsavedApplicationFields, hasApplicationDetails } from "../src/entities/application/model/application-form.ts";

test("property draft survives program switching and is omitted for innovation", () => {
  const d = { ...applicationDraft("BEACH"), orgInn: "1234567890", cadastralNumber: "61:18:123:45", rightHolder: "Организация", area: "123,45" };
  assert.equal(applicationPayload(d).property.area, "123.45");
  assert.equal(applicationPayload({ ...d, programCode: "INNOVATION" }).property, null);
  assert.equal(applicationPayload({ ...d, programCode: "MODERNIZATION" }).property.rightHolder, "Организация");
});
test("optional values stay empty and large amounts stay strings without arithmetic", () => {
  const d = applicationDraft("INNOVATION", null, "1234567890");
  assert.ok(canSubmitApplication(d));
  const a = applicationPayload({ ...d, requestedAmount: "12345678901234567,89" });
  assert.equal(a.requestedAmount, "12345678901234567.89");
  assert.equal(a.ownFunds, null);
  assert.deepEqual(applicationDraft(d.programCode, a), { ...d, requestedAmount: "12345678901234567.89" });
});
test("server nested field errors bind to visible controls", () => {
  assert.deepEqual(applicationErrors({ errors: ["application.signatory.fullName: Укажите имя", { field: "application.property.address", message: "Укажите адрес" }, "orgInn: Неверный формат"] }, "Ошибка"), { signerName: "Укажите имя", propertyAddress: "Укажите адрес", orgInn: "Неверный формат" });
});

test("old API dropping form fields cannot be reported as a complete save", () => {
  const wanted = applicationPayload({ ...applicationDraft("BEACH", null, "1234567890"), ownFunds: "100.00", rightHolder: "Организация" });
  assert.deepEqual(unsavedApplicationFields(wanted, { ...wanted, ownFunds: null, property: null }), ["ownFunds", "property.rightHolder"]);
  assert.deepEqual(unsavedApplicationFields(wanted, wanted), []);
});

test("unchanged old values are detected; equivalent decimal formatting is preserved", () => {
  const wanted = applicationPayload({ ...applicationDraft("INNOVATION", null, "1234567890"), orgName: "Новое имя", requestedAmount: "1000.0" });
  assert.deepEqual(unsavedApplicationFields(wanted, { ...wanted, orgName: "Старое имя", requestedAmount: "1000.00" }), ["orgName"]);
});
test("minimal creation stays compatible without discarding a partial filled application", () => {
  const draft = applicationDraft("INNOVATION", null, "1234567890");
  assert.equal(hasApplicationDetails(applicationPayload(draft)), false);
  assert.equal(hasApplicationDetails(applicationPayload({ ...draft, orgName: "Организация" })), true);
});

test("clearing optional data cannot succeed while the server keeps the old value", () => {
  const wanted = applicationPayload(applicationDraft("INNOVATION", null, "1234567890"));
  assert.deepEqual(unsavedApplicationFields(wanted, { ...wanted, project: "Старый проект", property: { cadastralNumber: "61:18:123:45" } }), ["project", "property"]);
  assert.deepEqual(unsavedApplicationFields(wanted, { ...wanted, ownFunds: undefined }), []);
});
