package ru.subsidy.package_.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import ru.subsidy.package_.upload.checks.FormatCheck;

/**
 * Цепочка проверок принимаемого файла (FR-03/06): пустой → размер → сигнатура (Tika, не расширение)
 * → целостность формата. Имя файла не используется.
 */
@Component
public class FileInspector {

    /** ZIP передаётся в ZipExpander, сам по себе не документ. */
    public static final String ZIP = "application/zip";

    private final Tika tika = new Tika();
    private final UploadLimits limits;
    private final List<FormatCheck> checks;

    /**
     * Конструктор.
     *
     * @param limits лимиты
     * @param checks проверки форматов
     */
    public FileInspector(UploadLimits limits, List<FormatCheck> checks) {
        this.limits = limits;
        this.checks = List.copyOf(checks);
    }

    /**
     * Проверяет файл.
     *
     * @param content содержимое
     * @return результат
     */
    public InspectionResult inspect(byte[] content) {
        InspectionResult result;
        if (content == null || content.length == 0) {
            result = InspectionResult.rejected(null, RejectReason.FILE_EMPTY);
        } else if (content.length > limits.maxBytes()) {
            result = InspectionResult.rejected(null, RejectReason.FILE_TOO_LARGE);
        } else {
            String mime = detect(content);
            if (ZIP.equals(mime)) {
                result = InspectionResult.accepted(mime, 0);
            } else {
                result = checks.stream().filter(check -> check.supports(mime)).findFirst()
                        .map(check -> check.inspect(content, mime, limits))
                        .orElseGet(() -> InspectionResult.rejected(mime, RejectReason.FILE_TYPE_UNSUPPORTED));
            }
        }
        return result;
    }

    private String detect(byte[] content) {
        try {
            return tika.detect(new ByteArrayInputStream(content));
        } catch (IOException unreadable) {
            return "application/octet-stream";
        }
    }
}
