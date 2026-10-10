package ru.subsidy.package_.upload;

/**
 * Архив не принят; сообщение — причина для специалиста.
 */
public class ZipRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Конструктор.
     *
     * @param reason причина
     */
    public ZipRejectedException(String reason) {
        super(reason);
    }
}
