/**
 * Типы зоны специалиста и кабинета заявителя (docs/API.md). Карточку собирает reviewApi из трёх сервисов:
 * package (пакет, файлы, документы), docproc (поля и проверки) и rules (протокол). Деньги и даты — строки, как пришли.
 */
export type OrgRef = { inn?: string; name?: string | null };
export type Contact = { email?: string | null; phone?: string | null };

/** Элемент очереди: базовые поля package + поля зоны /operator (могут отсутствовать на старом бэке). */
export type QueueItem = {
  id: string; org?: OrgRef; program?: string; status?: string; protocolStatus?: string | null;
  completeness?: number | null; failed?: number; warnings?: number; needsReview?: number; updatedAt?: string;
  applicantName?: string | null; assignee?: string | null; submittedAt?: string | null; waitingSeconds?: number | null;
  source?: "APPLICANT" | "SPECIALIST" | string; documents?: number; processing?: number; attention?: number; missing?: string[];
};
export type QueuePage = { items?: QueueItem[]; page?: { next?: string | null; total?: number } };

export type FieldCheck = { code: string; status: string; message?: string | null };
/** Поле документа (docproc): значение модели, как написано, уверенность и проверки. */
export type FieldCard = {
  name: string; title: string; value: string; raw?: string | null; confidence?: number | null;
  checks: FieldCheck[]; attention: boolean; page?: number | null; bbox?: number[] | null;
  /** Значение поправил специалист (до перезапроса карточки). */
  corrected?: boolean; modelValue?: string;
};
/** Файл документа: страницы документа — диапазон fromPage..toPage внутри файла. */
export type DocFile = { id: string; name?: string; version?: number; pages: number; offset: number };
export type DocumentCard = {
  /** documentId; для файла, который ещё не разделён на документы, — `file:<fileId>`. */
  id: string; fileId: string; fileName?: string; real: boolean;
  type?: string | null; typeName?: string | null; typeConfidence?: number | null; source?: string | null;
  stage?: string | null; reasonCode?: string | null; evidence?: string | null; model?: string | null; promptVersion?: string | null;
  fromPage: number; toPage: number; files: DocFile[]; fields: FieldCard[]; attention: boolean;
};
export type FileCard = {
  fileId: string; version?: number; name?: string; mime?: string; pages?: number | null; stage?: string; reasonCode?: string | null;
  documents?: { documentId: string; fromPage?: number; toPage?: number; type?: string | null; typeConfidence?: number | null; source?: string | null; stage?: string; reasonCode?: string | null }[];
};
export type ProtocolSummary = { status?: string; completeness?: number | null; failed?: number; warnings?: number; needsReview?: number };
export type Decision = { decision?: "APPROVE" | "REJECT" | "RETURN" | string; comment?: string | null; decidedBy?: string | null; decidedAt?: string | null };
export type OperatorRequest = { id?: string; type?: string | null; documentId?: string | null; reason?: string; createdAt?: string; resolvedAt?: string | null };

/** Анкета заявки (B1): тот же объект у заявителя и у специалиста. */
export type ApplicationData = {
  orgInn?: string | null; orgName?: string | null; ogrn?: string | null;
  address?: { value?: string | null; purpose?: "LEGAL" | "ACTUAL" | "PROJECT" | string | null } | null;
  project?: string | null; requestedAmount?: string | null; applicationDate?: string | null;
  signatory?: { fullName?: string | null; position?: string | null; authority?: string | null } | null;
  property?: { cadastralNumber?: string | null; address?: string | null; rightType?: "OWNERSHIP" | "LEASE" | "USE" | string | null } | null;
};

export type PackageRs = {
  packageId: string; status?: string; program?: string; org?: OrgRef; protocol?: ProtocolSummary | null; decision?: Decision | null;
  updatedAt?: string; files?: FileCard[]; contact?: Contact | null; requests?: OperatorRequest[]; assignee?: string | null;
  source?: string | null; submittedAt?: string | null; application?: ApplicationData | null; applicationRevision?: number;
};

export type Card = {
  id: string; status?: string; org: OrgRef; program?: string; assignee?: string | null; source?: string | null;
  submittedAt?: string | null; updatedAt?: string; contact?: Contact | null; requests: OperatorRequest[];
  summary: ProtocolSummary | null; decision: Decision | null; documents: DocumentCard[]; files: FileCard[];
  application?: ApplicationData | null; applicationRevision?: number;
};

export type HistoryEntry = { id: string; action?: string; actor?: string | null; source?: string | null; documentId?: string | null; details?: unknown; createdAt?: string };
export type Expiring = { packageId: string; inn?: string; documentId: string; type?: string; title?: string; date?: string; expiresAt?: string; daysLeft?: number };
export type ModelStatus = { provider?: string; model?: string; visionModel?: string; reachable?: boolean; error?: string };
export type DocType = { code: string; name?: string; hints?: string; schema?: { properties?: Record<string, { description?: string }> } };

/** Кабинет заявителя. */
export type Program = { code: string; name?: string; description?: string | null; deadline?: string | null };
export type RequiredDocument = { type: string; title?: string; hint?: string | null; required?: boolean; condition?: string | null };
export type ApplicantDocument = {
  id: string; fileId?: string; slotType?: string | null; detectedType?: string | null; detectedTypeName?: string | null;
  fileName?: string; pages?: number | null; stage?: string; reasonCode?: string | null; message?: string | null; hint?: string | null;
  electronicSignature?: unknown; updatedAt?: string;
};
export type ApplicantApplication = {
  applicationId: string; status?: string; programCode?: string; programName?: string | null;
  application?: ApplicationData | null; applicationRevision?: number;
  requiredDocuments?: RequiredDocument[]; documents?: ApplicantDocument[]; missing?: string[];
  operatorRequests?: OperatorRequest[]; decision?: Decision | null; submittedAt?: string | null; updatedAt?: string; createdAt?: string;
};
export type Created = { applicationId?: string; accessKey?: string; resumeCode?: string; link?: string };
export type Uploaded = { documentId?: string; fileId?: string; stage?: string; duplicateOf?: string | null; warnings?: string[] };
