package ru.subsidy.rules.protocol;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Протокол проверки (снимок).
 *
 * @param protocolId протокол
 * @param packageId пакет
 * @param version версия
 * @param status COMPLETE, INCOMPLETE, NEEDS_REVIEW
 * @param programCode программа
 * @param programVersion версия программы (параметры)
 * @param checklistVersion версия чек-листа (= версии программы)
 * @param rulesVersion версия набора правил
 * @param completeness {percent, items, missing, request}
 * @param results результаты правил
 * @param failed нарушений
 * @param warnings предупреждений
 * @param needsReview требует ручной проверки
 * @param createdAt когда
 */
public record ProtocolView(UUID protocolId, UUID packageId, int version, String status, String programCode,
                           int programVersion, int checklistVersion, String rulesVersion, JsonNode completeness,
                           List<Result> results, int failed, int warnings, int needsReview, OffsetDateTime createdAt) {

    /**
     * Результат правила.
     *
     * @param rule код
     * @param status статус
     * @param message текст
     * @param evidence где подтверждение
     * @param expected ожидалось
     * @param actual фактически
     */
    public record Result(String rule, String status, String message, JsonNode evidence, String expected,
                         String actual) {
    }

    /**
     * Строка истории версий.
     *
     * @param protocolId протокол
     * @param version версия
     * @param status статус
     * @param completeness %
     * @param failed нарушений
     * @param warnings предупреждений
     * @param needsReview на проверку
     * @param createdAt когда
     */
    public record Summary(UUID protocolId, int version, String status, int completeness, int failed, int warnings,
                          int needsReview, OffsetDateTime createdAt) {
    }
}
