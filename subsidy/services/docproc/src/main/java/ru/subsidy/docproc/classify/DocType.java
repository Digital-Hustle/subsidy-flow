package ru.subsidy.docproc.classify;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Вид документа из реестра (docs/DOC-TYPES.md).
 *
 * @param code код
 * @param name название
 * @param hints признаки для промпта
 * @param version версия схемы
 * @param kinds поле → вид нормализации (text, money, date, inn, ogrn, kpp, bik, account, bool, number, items, list)
 * @param schema JSON Schema полей
 */
public record DocType(String code, String name, String hints, String version, Map<String, String> kinds,
                      JsonNode schema) {
}
