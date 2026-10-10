import { test } from "node:test";
import assert from "node:assert/strict";
import { buildCard } from "../src/entities/application/model/fields.ts";
const packet = (version, documents = []) => ({ packageId: "p", files: [{ fileId: "f", version, stage: "UNREADABLE", documents, electronicSignature: { present: false, status: "ABSENT" } }] });

test("file without extracted documents remains selectable with signature metadata", () => {
  const card = buildCard(packet(1), [], () => null);
  assert.equal(card.documents[0].id, "file:f");
  assert.equal(card.documents[0].files[0].version, 1);
  assert.equal(card.documents[0].electronicSignature.present, false);
});
test("mapping keeps boolean false and excludes fields of an older file version", () => {
  const doc = { documentId: "d", fromPage: 1, toPage: 1 };
  const result = { documentId: "d", fileVersion: 1, fields: [{ name: "stampPresent", value: false }] };
  assert.equal(buildCard(packet(1, [doc]), [result], () => null).documents[0].fields[0].extractedValue, false);
  assert.deepEqual(buildCard(packet(2, [doc]), [result], () => null).documents[0].fields, []);
});
