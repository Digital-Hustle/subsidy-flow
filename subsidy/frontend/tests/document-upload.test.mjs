import { test } from "node:test";
import assert from "node:assert/strict";
import { splitDocumentFiles } from "../src/entities/document/model/upload.ts";

test("all case formats are selectable; archives and unrelated files are reported", () => {
  const supported = ["pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png"].map((ext) => new File(["content"], `scan.${ext.toUpperCase()}`));
  const zip = new File(["archive"], "documents.zip");
  const txt = new File(["text"], "note.txt");
  const result = splitDocumentFiles([...supported, zip, txt]);
  assert.deepEqual(result.accepted, supported);
  assert.deepEqual(result.rejected.map((r) => r.name), [zip.name, txt.name]);
  assert.ok(result.rejected.every((r) => r.detail && r.hint));
});

test("MIME supports files without extensions; server still validates content", () => {
  const file = new File(["content"], "scan", { type: "image/png" });
  const renamed = new File(["not a PDF"], "file.pdf");
  assert.deepEqual(splitDocumentFiles([file, renamed]).accepted, [file, renamed]);
  assert.deepEqual(splitDocumentFiles([]), { accepted: [], rejected: [] });
});
