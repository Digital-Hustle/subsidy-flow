package ru.subsidy.rules.facts;

import java.util.UUID;

/**
 * Файл пакета и его стадия разбора.
 *
 * @param fileId файл
 * @param version версия
 * @param stage стадия
 * @param reasonCode причина
 */
public record FileFacts(UUID fileId, int version, String stage, String reasonCode) {

    private static final String FAILED = "FAILED";
    private static final String UNREADABLE = "UNREADABLE";

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
