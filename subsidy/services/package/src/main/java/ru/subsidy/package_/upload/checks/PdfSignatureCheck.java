package ru.subsidy.package_.upload.checks;

import java.io.IOException;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;

import ru.subsidy.package_.upload.SignaturePresence;

/**
 * Проверяет наличие заполненных словарей подписи PDF, без CMS/сертификатов и сетевых вызовов.
 */
public final class PdfSignatureCheck {

    private static final int RANGE_PARTS = 4;

    private PdfSignatureCheck() {
    }

    /**
     * Наличие ЭП в уже открытом PDF.
     *
     * @param document PDF
     * @param size размер исходных байтов
     * @return наличие или причина неопределённости
     */
    public static SignaturePresence inspect(PDDocument document, int size) {
        boolean uncertain = false;
        try {
            for (PDSignature signature : document.getSignatureDictionaries()) {
                var value = signature.getCOSObject().getDictionaryObject(COSName.CONTENTS);
                COSString contents = value instanceof COSString text ? text : null;
                int[] range = signature.getByteRange();
                if (contents == null && range == null) {
                    continue;
                }
                if (contents != null && nonEmpty(contents.getBytes()) && validRange(range, size)) {
                    return SignaturePresence.determined(true);
                }
                uncertain = true;
            }
            return uncertain ? SignaturePresence.unknown("SIGNATURE_STRUCTURE_INCOMPLETE")
                    : SignaturePresence.determined(false);
        } catch (IOException | RuntimeException unreadable) {
            return SignaturePresence.unknown("SIGNATURE_INSPECTION_FAILED");
        }
    }

    private static boolean validRange(int[] range, int size) {
        return range != null && range.length == RANGE_PARTS && range[0] == 0 && range[1] > 0
                && range[2] > range[1] && range[3] >= 0 && (long) range[2] + range[3] <= size;
    }

    private static boolean nonEmpty(byte[] contents) {
        for (byte value : contents) {
            if (value != 0) {
                return true;
            }
        }
        return false;
    }
}
