package ru.subsidy.package_.upload;

/**
 * Итог проверки файла: принят (mime, страницы) или отклонён с кодом.
 *
 * @param mime определённый тип
 * @param pages число страниц (0, если неизвестно)
 * @param reason причина отказа или null
 */
public record InspectionResult(String mime, int pages, RejectReason reason) {

    /**
     * Принятый файл.
     *
     * @param mime тип
     * @param pages страницы
     * @return результат
     */
    public static InspectionResult accepted(String mime, int pages) {
        return new InspectionResult(mime, pages, null);
    }

    /**
     * Отклонённый файл.
     *
     * @param mime тип (может быть null)
     * @param reason причина
     * @return результат
     */
    public static InspectionResult rejected(String mime, RejectReason reason) {
        return new InspectionResult(mime, 0, reason);
    }

    /**
     * Принят ли файл.
     *
     * @return true, если причины отказа нет
     */
    public boolean isAccepted() {
        return reason == null;
    }
}
