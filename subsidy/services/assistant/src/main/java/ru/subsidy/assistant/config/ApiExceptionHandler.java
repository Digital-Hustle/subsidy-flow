package ru.subsidy.assistant.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.ProblemDetailFactory;

/**
 * Доменные ошибки → ProblemDetail с кодом из docs/API.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Map<ErrorCode, HttpStatus> STATUSES = Map.ofEntries(
            Map.entry(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.PACKAGE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.MODEL_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

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
}
