package ru.subsidy.rules.facts;

import java.util.UUID;

/**
 * Файл пакета и его стадия разбора.
 *
 * @param fileId файл
 * @param version версия
 * @param stage стадия
 * @param reasonCode причина
 * @param electronicSignature признак ЭП всего PDF
 */
public record FileFacts(UUID fileId, int version, String stage, String reasonCode, SignatureFacts electronicSignature) {

    private static final String FAILED = "FAILED";
    private static final String UNREADABLE = "UNREADABLE";

    /**
     * Совместимость со старыми событиями без проверки ЭП.
     *
     * @param fileId файл
     * @param version версия
     * @param stage стадия
     * @param reasonCode причина
     */
    public FileFacts(UUID fileId, int version, String stage, String reasonCode) {
        this(fileId, version, stage, reasonCode, SignatureFacts.stored(null, null));
    }

    /**
     * Разбор файла закончен (успешно или нет).
     *
     * @return true для PARSED, NEEDS_REVIEW, UNREADABLE, FAILED
     */
    public boolean done() {
        return switch (stage) {
            case "PARSED", "NEEDS_REVIEW", UNREADABLE, FAILED -> true;
            case null, default -> false;
        };
    }

    /**
     * Файл не прочитан или не разобран.
     *
     * @return true для UNREADABLE и FAILED
     */
    public boolean broken() {
        return UNREADABLE.equals(stage) || FAILED.equals(stage);
    }
}
