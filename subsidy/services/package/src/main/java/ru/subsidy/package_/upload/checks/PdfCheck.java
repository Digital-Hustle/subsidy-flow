package ru.subsidy.package_.upload.checks;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import ru.subsidy.package_.upload.InspectionResult;
import ru.subsidy.package_.upload.RejectReason;
import ru.subsidy.package_.upload.UploadLimits;

/**
 * PDF: открывается PDFBox, без пароля на открытие, страниц не больше лимита.
 */
@Component
public class PdfCheck implements FormatCheck {

    private static final String PDF = "application/pdf";

    @Override
    public boolean supports(String mime) {
        return PDF.equals(mime);
    }

    @Override
    public InspectionResult inspect(byte[] content, String mime, UploadLimits limits) {
        InspectionResult result;
        try (PDDocument doc = Loader.loadPDF(content)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                result = InspectionResult.rejected(PDF, RejectReason.FILE_CORRUPTED);
            } else if (pages > limits.maxPages()) {
                result = InspectionResult.rejected(PDF, RejectReason.TOO_MANY_PAGES);
            } else {
                result = InspectionResult.accepted(PDF, pages);
            }
        } catch (InvalidPasswordException encrypted) {
            result = InspectionResult.rejected(PDF, RejectReason.PDF_ENCRYPTED);
        } catch (IOException | RuntimeException broken) {
            result = InspectionResult.rejected(PDF, RejectReason.FILE_CORRUPTED);
        }
        return result;
    }
}
