package ru.subsidy.common.exception;

/**
 * Коды ошибок API (см. docs/API.md). Используются в ProblemDetail.code.
 */
public enum ErrorCode {

    // Организация / пакет
    INN_INVALID,
    OGRN_INVALID,
    ORGANIZATION_NOT_FOUND,
    PACKAGE_NOT_FOUND,
    PACKAGE_LOCKED,
    PACKAGE_DECIDED,
    PROGRAM_NOT_FOUND,

    // Файлы
    FILE_NOT_FOUND,
    FILE_TOO_LARGE,
    FILE_TYPE_UNSUPPORTED,
    FILE_DUPLICATE,
    ARCHIVE_INVALID,

    // Документы и проверки
    DOCUMENT_NOT_FOUND,
    DOCUMENT_TYPE_INVALID,
    FIELD_INVALID,
    PROTOCOL_NOT_FOUND,
    NOT_CHECKED_YET,
    MODEL_UNAVAILABLE,
    DEPENDENCY_UNAVAILABLE,

    // Вход сотрудников (ADR-023)
    LOGIN_CODE_INVALID,
    LOGIN_CODE_EXPIRED,
    LOGIN_CODE_LOCKED,
    LOGIN_RESEND_TOO_SOON,
    SESSION_EXPIRED,
    STAFF_LAST_SENIOR,
    MAIL_UNAVAILABLE,

    // System
    SYSTEM_ERROR,

    // Generic HTTP
    NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN,
    VALIDATION_ERROR;

    public String code() {
        return name();
    }
}
