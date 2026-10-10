package ru.subsidy.common.exception;

/**
 * Конфликт состояния → 409 ProblemDetail с кодом CONFLICT.
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
