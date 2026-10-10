package ru.subsidy.llm.api;

/**
 * Модель вернула ответ не по схеме и после перегенерации → NEEDS_REVIEW (MODEL_INVALID_OUTPUT).
 */
public class ModelInvalidOutputException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelInvalidOutputException(String message) {
        super(message);
    }
}
