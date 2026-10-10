package ru.subsidy.common.exception;

/**
 * Базовое доменное исключение — превращается в ProblemDetail с ErrorCode.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
