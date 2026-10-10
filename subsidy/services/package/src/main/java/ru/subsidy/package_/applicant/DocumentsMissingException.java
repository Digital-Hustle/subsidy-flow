package ru.subsidy.package_.applicant;

import java.util.List;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;

/**
 * Заявку нельзя отправить: не хватает документов или полей анкеты (422 DOCUMENTS_MISSING).
 */
public final class DocumentsMissingException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> missing;
    private final transient List<String> missingFields;

    /**
     * Конструктор.
     *
     * @param missing недостающие виды документов
     * @param missingFields незаполненные поля анкеты
     */
    public DocumentsMissingException(List<String> missing, List<String> missingFields) {
        super(ErrorCode.DOCUMENTS_MISSING, "Не хватает документов или данных анкеты");
        this.missing = List.copyOf(missing);
        this.missingFields = List.copyOf(missingFields);
    }

    /**
     * Недостающие виды документов.
     *
     * @return список
     */
    public List<String> missing() {
        return missing;
    }

    /**
     * Незаполненные поля анкеты.
     *
     * @return список
     */
    public List<String> missingFields() {
        return missingFields;
    }
}
