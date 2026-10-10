package ru.subsidy.common.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Превращает DomainException в RFC 7807 ProblemDetail с полями code и traceId.
 * Бин регистрируется в каждом сервисе через @ControllerAdvice (BaseExceptionHandler).
 */
public final class ProblemDetailFactory {

    private ProblemDetailFactory() {}

    /**
     * Создаёт ProblemDetail для доменного исключения.
     *
     * @param ex     доменное исключение
     * @param status HTTP-статус (обычно 400 для отказов прохода, 404 для NotFound)
     * @return ProblemDetail с type, title, detail, code
     */
    public static ProblemDetail of(DomainException ex, int status) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(ex.code().code());
        pd.setDetail(ex.getMessage());
        pd.setProperty("code", ex.code().code());
        return pd;
    }

    /**
     * Обёртка для ErrorResponseException, чтобы добавить поле code.
     */
    public static ProblemDetail withCode(ProblemDetail pd, ErrorCode code) {
        pd.setProperty("code", code.code());
        return pd;
    }
}
