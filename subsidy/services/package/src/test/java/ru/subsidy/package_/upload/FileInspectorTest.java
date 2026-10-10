package ru.subsidy.package_.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import ru.subsidy.package_.upload.checks.ImageCheck;
import ru.subsidy.package_.upload.checks.OfficeCheck;
import ru.subsidy.package_.upload.checks.PdfCheck;

class FileInspectorTest {

    private static final int MAX_PAGES = 3;
    private static final long MAX_BYTES = 200_000;
    private static final int SIDE = 64;

    private final FileInspector inspector = new FileInspector(new UploadLimits(MAX_BYTES, MAX_PAGES),
            List.of(new PdfCheck(), new ImageCheck(), new OfficeCheck()));

    private static byte[] pdf(int pages, String password) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int idx = 0; idx < pages; idx++) {
                doc.addPage(new PDPage());
            }
            if (password != null) {
                doc.protect(new StandardProtectionPolicy("owner", password, new AccessPermission()));
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void empty_rejected() {
        assertThat(inspector.inspect(new byte[0]).reason()).isEqualTo(RejectReason.FILE_EMPTY);
    }

    @Test
    void tooLarge_rejected() {
        assertThat(inspector.inspect(new byte[(int) MAX_BYTES + 1]).reason()).isEqualTo(RejectReason.FILE_TOO_LARGE);
    }

    @Test
    void plainText_unsupported() {
        byte[] text = "просто текст, не pdf".getBytes(StandardCharsets.UTF_8);
        assertThat(inspector.inspect(text).reason()).isEqualTo(RejectReason.FILE_TYPE_UNSUPPORTED);
    }

    @Test
    void validPdf_acceptedWithPages() throws IOException {
        InspectionResult result = inspector.inspect(pdf(2, null));
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.mime()).isEqualTo("application/pdf");
        assertThat(result.pages()).isEqualTo(2);
    }

    @Test
    void brokenPdf_corrupted() {
        byte[] broken = "%PDF-1.7\n1 0 obj << /Type /Catalog >> broken".getBytes(StandardCharsets.US_ASCII);
        assertThat(inspector.inspect(broken).reason()).isEqualTo(RejectReason.FILE_CORRUPTED);
    }

    @Test
    void encryptedPdf_rejected() throws IOException {
        assertThat(inspector.inspect(pdf(1, "secret")).reason()).isEqualTo(RejectReason.PDF_ENCRYPTED);
    }

    @Test
    void tooManyPages_rejected() throws IOException {
        assertThat(inspector.inspect(pdf(MAX_PAGES + 1, null)).reason()).isEqualTo(RejectReason.TOO_MANY_PAGES);
    }

    @Test
    void png_accepted_truncatedPng_corrupted() throws IOException {
        byte[] image = png();
        assertThat(inspector.inspect(image).isAccepted()).isTrue();
        byte[] truncated = Arrays.copyOf(image, image.length / 2);
        assertThat(inspector.inspect(truncated).reason()).isEqualTo(RejectReason.FILE_CORRUPTED);
    }

    @Test
    void docxAndXlsx_accepted() throws IOException {
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Заявка");
            doc.write(docx);
        }
        ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Смета");
            book.write(xlsx);
        }
        assertThat(inspector.inspect(docx.toByteArray()).mime()).isEqualTo(OfficeCheck.DOCX);
        assertThat(inspector.inspect(xlsx.toByteArray()).mime()).isEqualTo(OfficeCheck.XLSX);
    }
}
