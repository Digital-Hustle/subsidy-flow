package ru.subsidy.assistant.validate;

import java.util.List;

/**
 * Результат сверки чисел ответа с результатами инструментов.
 *
 * @param status OK или NUMBERS_MISMATCH
 * @param unknown числа и даты ответа, которых нет в данных инструментов
 */
public record Validation(String status, List<String> unknown) {

    /** Все числа подтверждены. */
    public static final String OK = "OK";

    /** Есть числа, которых нет в данных. */
    public static final String MISMATCH = "NUMBERS_MISMATCH";

    /**
     * Копия списка.
     *
     * @param status статус
     * @param unknown неподтверждённые значения
     */
    public Validation {
        unknown = List.copyOf(unknown);
    }
}
