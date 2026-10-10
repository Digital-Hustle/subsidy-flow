package ru.subsidy.package_.upload.checks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import ru.subsidy.package_.upload.InspectionResult;
import ru.subsidy.package_.upload.RejectReason;
import ru.subsidy.package_.upload.UploadLimits;

/**
 * JPG/PNG/TIFF: декодируется ImageIO (TwelveMonkeys), каждая страница TIFF читается.
 * HEIC принимается без декодирования — его открывает OCR-сервис (pillow-heif).
 */
@Component
public class ImageCheck implements FormatCheck {

    private static final String HEIC = "image/heic";
    private static final Set<String> DECODABLE = Set.of("image/jpeg", "image/png", "image/tiff");

    @Override
    public boolean supports(String mime) {
        return DECODABLE.contains(mime) || HEIC.equals(mime) || "image/heif".equals(mime);
    }

    @Override
    public InspectionResult inspect(byte[] content, String mime, UploadLimits limits) {
        if (!DECODABLE.contains(mime)) {
            return InspectionResult.accepted(HEIC, 1);
        }
        InspectionResult result;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (readers.hasNext()) {
                result = read(readers.next(), input, mime, limits);
            } else {
                result = InspectionResult.rejected(mime, RejectReason.FILE_CORRUPTED);
            }
        } catch (IOException | RuntimeException broken) {
            result = InspectionResult.rejected(mime, RejectReason.FILE_CORRUPTED);
        }
        return result;
    }

    private static InspectionResult read(ImageReader reader, ImageInputStream input, String mime,
                                         UploadLimits limits) throws IOException {
        try {
            reader.setInput(input);
            int pages = reader.getNumImages(true);
            InspectionResult result;
            if (pages > limits.maxPages()) {
                result = InspectionResult.rejected(mime, RejectReason.TOO_MANY_PAGES);
            } else if (pages <= 0) {
                result = InspectionResult.rejected(mime, RejectReason.FILE_CORRUPTED);
            } else {
                for (int idx = 0; idx < pages; idx++) {
                    reader.read(idx);
                }
                result = InspectionResult.accepted(mime, pages);
            }
            return result;
        } finally {
            reader.dispose();
        }
    }
}
