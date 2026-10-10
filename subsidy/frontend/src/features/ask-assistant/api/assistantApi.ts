import { api } from "@/shared/api";

export type Source = string | Record<string, unknown>;
export type AskAnswer = {
  messageId: string; answer: string; sources: Source[] | null; tools: string[]; model: string | null; createdAt: string;
  validator: { status: string; details: string[] } | null;
};
export type ChatMessage = {
  messageId: string; role: "user" | "assistant" | string; content: string; sources: Source[] | null;
  validatorStatus: string | null; validatorDetails: unknown; createdAt: string;
};

/** AS-01..05: вопрос по пакету, история, подсказки. Ответ строится только из инструментов rules. */
export const assistantApi = {
  ask: (packageId: string, question: string) => api<AskAnswer>("assistant", `/packages/${packageId}/ask`, { method: "POST", body: { question } }),
  messages: (packageId: string) => api<{ items: ChatMessage[] }>("assistant", `/packages/${packageId}/messages`),
  suggestions: (packageId: string) => api<{ items: string[] }>("assistant", `/packages/${packageId}/suggestions`),
};

export const assistantKeys = {
  messages: (packageId: string) => ["assistant", "messages", packageId] as const,
  suggestions: (packageId: string) => ["assistant", "suggestions", packageId] as const,
};
