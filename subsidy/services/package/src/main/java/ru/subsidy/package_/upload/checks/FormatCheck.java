package ru.subsidy.package_.upload.checks;

import ru.subsidy.package_.upload.InspectionResult;
import ru.subsidy.package_.upload.UploadLimits;

/**
 * Проверка целостности файла конкретного формата.
 */
public interface FormatCheck {

    /**
     * Подходит ли проверка для типа, определённого Tika.
     *
     * @param mime тип
     * @return true, если проверка обрабатывает тип
     */
    boolean supports(String mime);

    /**
     * Открывает файл и считает страницы. Любое исключение парсера — FILE_CORRUPTED.
     *
     * @param content содержимое
     * @param mime тип
     * @param limits лимиты
     * @return результат
     */
    InspectionResult inspect(byte[] content, String mime, UploadLimits limits);
}
