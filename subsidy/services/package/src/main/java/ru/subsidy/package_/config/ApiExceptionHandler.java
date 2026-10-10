package ru.subsidy.package_.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.ProblemDetailFactory;
import ru.subsidy.package_.applicant.DocumentsMissingException;
import ru.subsidy.package_.applicant.UploadRejectedException;

/**
 * Доменные ошибки → ProblemDetail с кодом из docs/API.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Map<ErrorCode, HttpStatus> STATUSES = Map.ofEntries(
            Map.entry(ErrorCode.LOGIN_CODE_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.LOGIN_CODE_EXPIRED, HttpStatus.GONE),
            Map.entry(ErrorCode.LOGIN_CODE_LOCKED, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(ErrorCode.LOGIN_RESEND_TOO_SOON, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(ErrorCode.SESSION_EXPIRED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.STAFF_LAST_SENIOR, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MAIL_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ErrorCode.INN_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.PROGRAM_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.PACKAGE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.PACKAGE_LOCKED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.PACKAGE_DECIDED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.CONSENT_REQUIRED, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.PROGRAM_UNKNOWN, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS));

    /**
     * Доменное исключение.
     *
     * @param exception исключение
     * @return ProblemDetail (статус берётся из поля status)
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail domain(DomainException exception) {
        HttpStatus status = STATUSES.getOrDefault(exception.code(), HttpStatus.UNPROCESSABLE_ENTITY);
        return ProblemDetailFactory.of(exception, status.value());
    }

    /**
     * Не хватает документов или анкеты для отправки.
     *
     * @param exception исключение
     * @return 422 с missing и missingFields
     */
    @ExceptionHandler(DocumentsMissingException.class)
    public ProblemDetail missing(DocumentsMissingException exception) {
        ProblemDetail problem = ProblemDetailFactory.of(exception, HttpStatus.UNPROCESSABLE_ENTITY.value());
        problem.setProperty("missing", exception.missing());
        problem.setProperty("missingFields", exception.missingFields());
        return problem;
    }

    /**
     * Файл заявителя не принят.
     *
     * @param exception исключение
     * @return 422 с кодом отказа и подсказкой
     */
    @ExceptionHandler(UploadRejectedException.class)
    public ProblemDetail rejected(UploadRejectedException exception) {
        ProblemDetail problem = ProblemDetailFactory.of(exception, HttpStatus.UNPROCESSABLE_ENTITY.value());
        problem.setTitle(exception.reason());
        problem.setProperty("code", exception.reason());
        problem.setProperty("hint", exception.hint());
        return problem;
    }
}
