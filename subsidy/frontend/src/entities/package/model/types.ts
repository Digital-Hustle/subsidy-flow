/** Типы ответов package и docproc (docs/API.md). Деньги и даты — строки, как пришли с сервера. */
export type PackageStatus = "COLLECTING" | "PROCESSING" | "CHECKED" | "DECIDED";
export type ProtocolStatus =
  | "COMPLETE" | "INCOMPLETE" | "NEEDS_REVIEW";
export type Decision = "APPROVE" | "REJECT" | "RETURN";
export type OrgRef = { inn: string; name: string | null };

export type QueueItem = {
  id: string; org: OrgRef; program: string; status: PackageStatus; protocolStatus: ProtocolStatus | null;
  completeness: number | null; failed: number; warnings: number; needsReview: number; updatedAt: string;
};
export type QueuePage = { items: QueueItem[]; page: { next: string | null; total: number } };
export type QueueFilter = { status?: string; inn?: string; program?: string };

export type DocumentCard = {
  documentId: string; fromPage: number; toPage: number; type: string | null; typeConfidence: number | null;
  source: string | null; stage: string; reasonCode: string | null;
};
export type FileCard = {
  fileId: string; version: number; name: string; mime: string; pages: number | null; stage: string;
  reasonCode: string | null; documents: DocumentCard[];
};
export type PackageCard = {
  packageId: string; status: PackageStatus; program: string; org: OrgRef;
  protocol: { status: ProtocolStatus; completeness: number | null; failed: number; warnings: number; needsReview: number } | null;
  decision: { decision: Decision; comment: string | null; decidedBy: string; decidedAt: string } | null;
  updatedAt: string; files: FileCard[];
};
export type Created = { packageId: string; status: PackageStatus; program: string; org: OrgRef };
export type Rejected = { name: string; code: string; detail: string; hint: string | null };
export type UploadResult = { files: { fileId: string; stage: string; duplicateOf: string | null }[]; rejected: Rejected[] };
export type DecisionResult = { packageId: string; status: PackageStatus; decision: Decision; decidedBy: string; decidedAt: string };

export type FieldCheck = { code: string; status: string; message: string };
export type ParsedField = { name: string; value: unknown; raw: string | null; confidence: number | null; checks: FieldCheck[] };
export type DocumentResult = {
  documentId: string; fileId: string; fileVersion: number; fromPage: number; toPage: number; stage: string;
  reasonCode: string | null; type: string | null; typeConfidence: number | null; evidence: string | null;
  source: string | null; resultVersion: number | null; fields: ParsedField[] | null; model: string | null;
};
export type DocType = { code: string; name: string; hints: string | null };
export type ModelStatus = { provider: string | null; model: string | null; visionModel: string | null; reachable: boolean; error: string | null };
