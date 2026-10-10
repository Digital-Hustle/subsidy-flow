package ru.subsidy.rules.facts;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Признак ЭП всего PDF; действительность и подписи отдельных страниц не проверяются.
 *
 * @param status PRESENT, ABSENT, UNKNOWN
 * @param present наличие, null при неопределённости
 * @param reason причина неопределённости
 * @param cryptographicallyVerified всегда false
 */
public record SignatureFacts(String status, @Schema(nullable = true) Boolean present, String reason,
                             boolean cryptographicallyVerified) {

    private static final String PRESENT = "PRESENT";
    private static final String ABSENT = "ABSENT";
    private static final String UNKNOWN = "UNKNOWN";

    /**
     * Восстановление признака из проекции; старые события дают UNKNOWN.
     *
     * @param status сохранённый статус
     * @param reason причина
     * @return признак
     */
    public static SignatureFacts stored(String status, String reason) {
        return switch (status) {
            case PRESENT -> new SignatureFacts(PRESENT, true, null, false);
            case ABSENT -> new SignatureFacts(ABSENT, false, null, false);
            case null, default -> new SignatureFacts(UNKNOWN, null, reason == null ? "NOT_INSPECTED" : reason, false);
        };
    }
}
