package ru.subsidy.docproc.text;

/**
 * Текст страницы файла.
 *
 * @param page номер страницы с 1
 * @param text текст
 * @param source TEXT_LAYER, OCR, OCR_WEAK, OFFICE или NONE (текста нет — только картинка)
 * @param tables таблицы страницы текстом (OCR-06) или null
 */
public record PageText(int page, String text, String source, String tables) {

    /** Текстовый слой PDF. */
    public static final String TEXT_LAYER = "TEXT_LAYER";
    /** Распознано OCR. */
    public static final String OCR = "OCR";
    /** Распознано OCR с низкой уверенностью: текст есть, но модели лучше показать и картинку (DP-14). */
    public static final String OCR_WEAK = "OCR_WEAK";
    /** DOCX/XLSX. */
    public static final String OFFICE = "OFFICE";
    /** Текста нет. */
    public static final String NONE = "NONE";

    /**
     * Страница без таблиц.
     *
     * @param page номер
     * @param text текст
     * @param source источник
     */
    public PageText(int page, String text, String source) {
        this(page, text, source, null);
    }

    /**
     * Есть ли на странице текст.
     *
     * @return true, если текст не пустой
     */
    public boolean hasText() {
        return !NONE.equals(source) && text != null && !text.isBlank();
    }

    /**
     * Нужна ли модели картинка страницы: текста нет или OCR слабый.
     *
     * @return true для NONE, пустого текста и OCR_WEAK
     */
    public boolean needsImage() {
        return !hasText() || OCR_WEAK.equals(source);
    }

    /**
     * Та же страница с таблицами.
     *
     * @param rendered таблицы текстом
     * @return страница
     */
    public PageText withTables(String rendered) {
        return new PageText(page, text, source, rendered == null || rendered.isBlank() ? null : rendered);
    }

    /**
     * Текст для извлечения полей: текст страницы и её таблицы.
     *
     * @return текст
     */
    public String textWithTables() {
        return tables == null || tables.isBlank() ? text : text + "\n\n" + tables;
    }
}
