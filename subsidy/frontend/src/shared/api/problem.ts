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

/** Русский текст по статусу — когда бэк (gateway, Spring по умолчанию, прокси) ответил без русского detail. */
const BY_STATUS: Record<number, string> = {
  400: "Запрос не принят — проверьте введённые данные",
  401: "Сессия истекла — войдите снова",
  403: "Недостаточно прав для этого действия",
  404: "Не найдено — возможно, запись удалили",
  409: "Данные уже изменились — обновите страницу",
  413: "Файл слишком большой",
  415: "Этот формат файла не поддерживается",
  422: "Данные не прошли проверку",
  429: "Слишком много запросов — подождите немного",
  500: "Ошибка на сервере — попробуйте ещё раз",
  502: "Сервис временно недоступен",
  503: "Сервис временно недоступен",
  504: "Сервер не ответил вовремя — попробуйте ещё раз",
};
const CYR = /[а-яё]/i;

/** Текст ошибки для человека: русский detail с бэка; английский/технический заменяем текстом по статусу. */
export const errorText = (e: unknown) => {
  if (!(e instanceof ApiError)) return NETWORK;
  if (CYR.test(e.message)) return e.message;
  return BY_STATUS[e.status] ?? (e.status >= 500 ? BY_STATUS[500] : "Не получилось — попробуйте ещё раз");
};
