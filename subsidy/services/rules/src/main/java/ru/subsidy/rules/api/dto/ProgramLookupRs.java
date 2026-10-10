package ru.subsidy.rules.api.dto;

/**
 * Короткая карточка программы для приёма пакета.
 *
 * @param code код
 * @param version версия
 */
public record ProgramLookupRs(String code, int version) {
}
