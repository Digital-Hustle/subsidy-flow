package ru.subsidy.package_.upload;

/**
 * Коды отказа приёма файла с текстами для специалиста (docs/API.md).
 */
public enum RejectReason {
    /** Пустой файл. */
    FILE_EMPTY("Файл пустой", "Выберите файл ещё раз"),
    /** Больше лимита. */
    FILE_TOO_LARGE("Файл больше допустимого размера", "Разделите документ или уменьшите разрешение скана"),
    /** Формат не поддерживается. */
    FILE_TYPE_UNSUPPORTED("Такой формат не поддерживается",
            "Подойдут PDF, JPG, PNG, TIFF, HEIC, DOCX, XLSX, ZIP"),
    /** Не открывается. */
    FILE_CORRUPTED("Файл повреждён и не открывается", "Сохраните документ заново"),
    /** PDF под паролем. */
    PDF_ENCRYPTED("PDF защищён паролем", "Снимите пароль и загрузите снова"),
    /** Слишком много страниц. */
    TOO_MANY_PAGES("Слишком много страниц", "Разделите файл"),
    /** Архив не принят. */
    ZIP_UNSAFE("Архив не принят", "Упакуйте файлы заново без вложенных архивов"),
    /** Тот же файл (sha256) уже есть в пакете. */
    DUPLICATE_FILE("Этот файл уже есть в пакете", "Загрузите другой документ"),
    /** Лимит файлов в пакете. */
    TOO_MANY_FILES("В пакете уже максимум файлов", "Удалите лишние");

    private final String detail;
    private final String hint;

    RejectReason(String detail, String hint) {
        this.detail = detail;
        this.hint = hint;
    }

    /**
     * Текст ошибки.
     *
     * @return что случилось
     */
    public String detail() {
        return detail;
    }

    /**
     * Подсказка.
     *
     * @return что сделать
     */
    public String hint() {
        return hint;
    }
}
