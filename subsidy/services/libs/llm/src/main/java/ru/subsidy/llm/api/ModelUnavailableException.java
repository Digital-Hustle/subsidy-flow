package ru.subsidy.llm.api;

/**
 * Модель недоступна → стадия WAITING_MODEL и автоповтор (не «готово»).
 */
public class ModelUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
