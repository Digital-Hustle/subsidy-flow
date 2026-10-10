package ru.subsidy.package_.upload.checks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import ru.subsidy.package_.upload.InspectionResult;
import ru.subsidy.package_.upload.RejectReason;
import ru.subsidy.package_.upload.UploadLimits;

/**
 * DOCX/XLSX: открывается POI. Tika core видит OOXML как контейнер — точный тип определяем здесь.
 */
@Component
public class OfficeCheck implements FormatCheck {

    /** DOCX. */
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    /** XLSX. */
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String OOXML = "application/x-tika-ooxml";
    private static final Pattern WORD_PART = Pattern.compile("/word/document\\.xml");
    private static final Pattern SHEET_PART = Pattern.compile("/xl/workbook\\.xml");

    @Override
    public boolean supports(String mime) {
        return OOXML.equals(mime) || DOCX.equals(mime) || XLSX.equals(mime);
    }

    @Override
    public InspectionResult inspect(byte[] content, String mime, UploadLimits limits) {
        InspectionResult result;
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content))) {
            if (!pkg.getPartsByName(WORD_PART).isEmpty()) {
                try (XWPFDocument doc = new XWPFDocument(pkg)) {
                    result = InspectionResult.accepted(DOCX, Math.max(1, doc.getProperties()
                            .getExtendedProperties().getPages()));
                }
            } else if (!pkg.getPartsByName(SHEET_PART).isEmpty()) {
                try (XSSFWorkbook book = new XSSFWorkbook(pkg)) {
                    result = InspectionResult.accepted(XLSX, Math.max(1, book.getNumberOfSheets()));
                }
            } else {
                result = InspectionResult.rejected(mime, RejectReason.FILE_TYPE_UNSUPPORTED);
            }
        } catch (IOException | InvalidFormatException | RuntimeException broken) {
            result = InspectionResult.rejected(mime, RejectReason.FILE_CORRUPTED);
        }
        return result;
    }
}
