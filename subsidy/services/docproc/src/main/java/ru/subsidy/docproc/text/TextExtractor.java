package ru.subsidy.docproc.text;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Текст страниц: PDF — текстовый слой PDFBox, сканы и изображения — ocr, DOCX/XLSX — Apache POI.
 */
@Component
public class TextExtractor {

    /** PDF. */
    public static final String PDF = "application/pdf";
    private static final String OCR_UNAVAILABLE = "ocr unavailable: {}";
    private static final Logger LOG = LoggerFactory.getLogger(TextExtractor.class);
    private static final int MIN_TEXT_CHARS = 30;
    private static final float RENDER_DPI = 110f;
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final OcrClient ocr;

    /**
     * Конструктор.
     *
     * @param ocr клиент ocr
     */
    public TextExtractor(OcrClient ocr) {
        this.ocr = ocr;
    }

    /**
     * Текст всех страниц.
     *
     * @param content файл
     * @param mime тип
     * @return страницы (у страниц без текста source = NONE)
     * @throws UncheckedIOException файл не читается
     */
    public List<PageText> extract(byte[] content, String mime) {
        try {
            return switch (mime) {
                case PDF -> pdf(content);
                case DOCX -> office(docx(content));
                case XLSX -> office(xlsx(content));
                case null, default -> image(content, mime);
            };
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Картинки страниц для vision-модели (PNG).
     *
     * @param content файл
     * @param mime тип
     * @param fromPage первая страница
     * @param toPage последняя страница
     * @return PNG по странице
     * @throws UncheckedIOException файл не читается
     */
    public List<byte[]> images(byte[] content, String mime, int fromPage, int toPage) {
        try {
            if (!PDF.equals(mime)) {
                return List.of(content);
            }
            List<byte[]> result = new ArrayList<>();
            try (PDDocument doc = Loader.loadPDF(content)) {
                PDFRenderer renderer = new PDFRenderer(doc);
                for (int page = fromPage; page <= Math.min(toPage, doc.getNumberOfPages()); page++) {
                    BufferedImage image = renderer.renderImageWithDPI(page - 1, RENDER_DPI, ImageType.RGB);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", out);
                    result.add(out.toByteArray());
                }
            }
            return result;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private List<PageText> pdf(byte[] content) throws IOException {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc).strip();
                pages.add(new PageText(page, text, text.length() >= MIN_TEXT_CHARS ? PageText.TEXT_LAYER
                        : PageText.NONE));
            }
        }
        if (ocr.enabled()) {
            // ocr нужен и для страниц с текстовым слоем: таблицы позиций (OCR-06, pdfplumber)
            return mergeOcr(pages, content);
        }
        return pages;
    }

    private List<PageText> mergeOcr(List<PageText> pages, byte[] content) {
        try {
            List<PageText> recognized = ocr.extract(content, PDF);
            List<PageText> merged = new ArrayList<>();
            for (PageText page : pages) {
                PageText replacement = page;
                if (page.page() <= recognized.size()) {
                    PageText ocrPage = recognized.get(page.page() - 1);
                    replacement = page.hasText() ? page.withTables(ocrPage.tables()) : ocrPage;
                }
                merged.add(replacement);
            }
            return merged;
        } catch (RuntimeException failure) {
            LOG.warn(OCR_UNAVAILABLE, failure.getClass().getSimpleName());
            return pages;
        }
    }

    private List<PageText> image(byte[] content, String mime) {
        if (ocr.enabled()) {
            try {
                return ocr.extract(content, mime);
            } catch (RuntimeException failure) {
                LOG.warn(OCR_UNAVAILABLE, failure.getClass().getSimpleName());
            }
        }
        return List.of(new PageText(1, "", PageText.NONE));
    }

    private static String docx(byte[] content) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String xlsx(byte[] content) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(content));
             XSSFExcelExtractor extractor = new XSSFExcelExtractor(book)) {
            return extractor.getText();
        }
    }

    private static List<PageText> office(String text) {
        String clean = text == null ? "" : text.strip();
        return List.of(new PageText(1, clean, clean.isEmpty() ? PageText.NONE : PageText.OFFICE));
    }
}
