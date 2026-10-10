package ru.subsidy.docproc.api.dto;

import java.util.List;

/**
 * Коллекция в ответе.
 *
 * @param items элементы
 * @param <T> тип элемента
 */
public record Items<T>(List<T> items) {
}
