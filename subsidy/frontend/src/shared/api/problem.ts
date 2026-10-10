/** Ошибка API в формате application/problem+json: текст и подсказка — с бэка, по-русски. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly hint?: string;
  readonly traceId?: string;
  readonly params: Record<string, unknown>;

  constructor(status: number, code: string, detail: string, hint?: string, traceId?: string, params: Record<string, unknown> = {}) {
    super(detail);
    this.status = status;
    this.code = code;
    this.hint = hint;
    this.traceId = traceId;
    this.params = params;
  }
}

const NETWORK = "Нет связи с сервером";

/** Разбор ответа ошибки: problem+json → ApiError; иначе — общий текст по статусу. */
export const toApiError = async (res: Response): Promise<ApiError> => {
  try {
    const p = (await res.json()) as Record<string, unknown>;
    const detail = typeof p.detail === "string" ? p.detail : typeof p.title === "string" ? p.title : `Ошибка ${res.status}`;
    return new ApiError(res.status, String(p.code ?? res.status), detail, p.hint as string | undefined, p.traceId as string | undefined, p);
  } catch {
    return new ApiError(res.status, String(res.status), res.status >= 500 ? "Сервис временно недоступен" : `Ошибка ${res.status}`);
  }
};

export const networkError = () => new ApiError(0, "NETWORK", NETWORK, "Проверьте интернет и повторите");

/** Текст ошибки для человека: detail с бэка, никогда не сырой stack. */
export const errorText = (e: unknown) => (e instanceof ApiError ? e.message : NETWORK);
