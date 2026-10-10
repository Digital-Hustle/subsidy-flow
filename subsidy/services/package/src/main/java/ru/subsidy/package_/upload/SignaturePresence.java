package ru.subsidy.package_.upload;

/**
 * Наличие встроенной ЭП; криптографическая действительность не проверяется.
 *
 * @param status PRESENT, ABSENT или UNKNOWN
 * @param present true, false или null, если определить нельзя
 * @param reason причина неопределённости или null
 * @param cryptographicallyVerified всегда false
 */
public record SignaturePresence(String status, Boolean present, String reason, boolean cryptographicallyVerified) {

    /**
     * Читает сохранённое состояние версии файла.
     *
     * @param status состояние
     * @param reason причина
     * @return результат
     */
    public static SignaturePresence stored(String status, String reason) {
        return "UNKNOWN".equals(status) ? unknown(reason) : determined("PRESENT".equals(status));
    }

    /**
     * Результат наличия.
     *
     * @param present есть ли заполненная структура подписи
     * @return результат
     */
    public static SignaturePresence determined(boolean present) {
        return new SignaturePresence(present ? "PRESENT" : "ABSENT", present, null, false);
    }

    /**
     * Неопределённый результат.
     *
     * @param reason причина
     * @return результат
     */
    public static SignaturePresence unknown(String reason) {
        return new SignaturePresence("UNKNOWN", null, reason, false);
    }
}
