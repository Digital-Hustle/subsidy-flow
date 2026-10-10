package ru.subsidy.rules.completeness;

/**
 * Чего не хватает и почему (FR-37).
 *
 * @param item пункт
 * @param type нужный вид (или несколько через «или»)
 * @param reason почему нужен
 */
public record Missing(String item, String type, String reason) {
}
