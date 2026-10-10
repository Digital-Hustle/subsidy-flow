package ru.subsidy.package_.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.junit.jupiter.api.Test;

import ru.subsidy.package_.upload.checks.PdfCheck;

class PdfSignatureCheckTest {

    private final FileInspector inspector = new FileInspector(new UploadLimits(200_000, 3), List.of(new PdfCheck()));

    static byte[] plain() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    static byte[] signed() throws IOException {
        try (PDDocument document = Loader.loadPDF(plain()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            // Заведомо не CMS: тест доказывает наличие структуры, а не криптографическую действительность.
            document.addSignature(signature, content -> new byte[] {1, 2, 3});
            document.saveIncremental(output);
            return output.toByteArray();
        }
    }

    @Test
    void unsignedPdf_absent() throws IOException {
        assertThat(inspector.inspect(plain()).electronicSignature()).isEqualTo(SignaturePresence.determined(false));
    }

    @Test
    void filledSignature_presentEvenWithoutValidCms() throws IOException {
        SignaturePresence result = inspector.inspect(signed()).electronicSignature();
        assertThat(result.status()).isEqualTo("PRESENT");
        assertThat(result.present()).isTrue();
        assertThat(result.cryptographicallyVerified()).isFalse();
    }

    @Test
    void emptySignatureField_isNotASignature() throws IOException {
        assertThat(inspector.inspect(withField(false)).electronicSignature().status()).isEqualTo("ABSENT");
    }

    @Test
    void incompleteDictionary_unknownWithReason() throws IOException {
        SignaturePresence result = inspector.inspect(withField(true)).electronicSignature();
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.present()).isNull();
        assertThat(result.reason()).isEqualTo("SIGNATURE_STRUCTURE_INCOMPLETE");
        assertThat(result.cryptographicallyVerified()).isFalse();
    }

    private static byte[] withField(boolean malformed) throws IOException {
        try (PDDocument document = Loader.loadPDF(plain()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDAcroForm form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);
            PDSignatureField field = new PDSignatureField(form);
            form.getFields().add(field);
            if (malformed) {
                PDSignature signature = new PDSignature();
                signature.setContents(new byte[] {1, 2, 3});
                field.setValue(signature);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
