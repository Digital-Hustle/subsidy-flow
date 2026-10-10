package ru.subsidy.package_.applicant;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;

/**
 * Файл заявителя не принят; код — из таблицы «Коды отказов приёма» (422).
 */
public final class UploadRejectedException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final String reason;
    private final String hint;

    /**
     * Конструктор.
     *
     * @param reason код отказа (RejectReason)
     * @param detail текст
     * @param hint что сделать
     */
    public UploadRejectedException(String reason, String detail, String hint) {
        super(ErrorCode.VALIDATION_ERROR, detail);
        this.reason = reason;
        this.hint = hint;
    }

    /**
     * Код отказа.
     *
     * @return код
     */
    public String reason() {
        return reason;
    }

    /**
     * Подсказка.
     *
     * @return текст
     */
    public String hint() {
        return hint;
    }
}
