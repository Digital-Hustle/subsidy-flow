package ru.subsidy.common.exception;

/**
 * Ресурс не найден → 404 ProblemDetail с кодом NOT_FOUND.
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
