package ru.subsidy.package_.preview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.NotFoundException;

/**
 * Рендер одной страницы в PNG: PDF — PDFBox, изображения (в т.ч. многостраничный TIFF) — ImageIO.
 */
@Component
public class PageRenderer {

    private static final float DPI = 110f;
    private static final String PDF = "application/pdf";
    private static final String IMAGE_PREFIX = "image/";
    private static final String NO_PAGE = "Такой страницы нет";

    /**
     * Рендерит страницу.
     *
     * @param content содержимое файла
     * @param mime тип файла
     * @param page номер страницы с 1
     * @return PNG
     * @throws NotFoundException страницы нет
     * @throws DomainException FILE_TYPE_UNSUPPORTED — для типа нет превью
     * @throws UncheckedIOException файл не читается
     */
    public byte[] render(byte[] content, String mime, int page) {
        if (page < 1) {
            throw new NotFoundException(NO_PAGE);
        }
        try {
            BufferedImage image;
            if (PDF.equals(mime)) {
                image = renderPdf(content, page);
            } else if (mime != null && mime.startsWith(IMAGE_PREFIX)) {
                image = readImage(content, page);
            } else {
                throw new DomainException(ErrorCode.FILE_TYPE_UNSUPPORTED, "Для этого типа файла нет превью");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static BufferedImage renderPdf(byte[] content, int page) throws IOException {
        try (PDDocument doc = Loader.loadPDF(content)) {
            if (page > doc.getNumberOfPages()) {
                throw new NotFoundException(NO_PAGE);
            }
            return new PDFRenderer(doc).renderImageWithDPI(page - 1, DPI, ImageType.RGB);
        }
    }

    private static BufferedImage readImage(byte[] content, int page) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new DomainException(ErrorCode.FILE_TYPE_UNSUPPORTED, "Изображение не читается");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (page > reader.getNumImages(true)) {
                    throw new NotFoundException(NO_PAGE);
                }
                return reader.read(page - 1);
            } finally {
                reader.dispose();
            }
        }
    }
}
