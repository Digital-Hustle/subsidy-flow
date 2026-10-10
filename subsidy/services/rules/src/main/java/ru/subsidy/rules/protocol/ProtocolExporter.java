package ru.subsidy.rules.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;

/**
 * Выгрузка протокола в DOCX (POI), XLSX (POI) и PDF (PDFBox, шрифт Liberation Sans с кириллицей) — B-02.
 */
@Component
public class ProtocolExporter {

    /** DOCX. */
    public static final String DOCX = "docx";
    /** XLSX. */
    public static final String XLSX = "xlsx";
    /** PDF. */
    public static final String PDF = "pdf";

    private static final String[] HEADER = {"Правило", "Статус", "Пояснение", "Ожидалось", "Фактически"};
    private static final float MARGIN = 40;
    private static final float LEADING = 14;
    private static final float FONT_SIZE = 10;
    private static final float TITLE_SIZE = 14;
    private static final int WRAP = 105;
    private static final int[] WIDTHS = {26, 16, 80, 20, 20};
    private static final int CHAR_WIDTH = 256;
    private static final int COL_RULE = 0;
    private static final int COL_STATUS = 1;
    private static final int COL_MESSAGE = 2;
    private static final String DASH = "—";

    /**
     * Выгружает протокол.
     *
     * @param protocol протокол
     * @param format docx, xlsx или pdf
     * @return файл
     * @throws DomainException VALIDATION_ERROR для неизвестного формата
     * @throws UncheckedIOException ошибка записи
     */
    public byte[] export(ProtocolView protocol, String format) {
        try {
            return switch (format) {
                case DOCX -> docx(protocol);
                case XLSX -> xlsx(protocol);
                case PDF -> pdf(protocol);
                case null, default -> throw new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Формат выгрузки: docx, xlsx или pdf");
            };
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<String> summary(ProtocolView protocol) {
        List<String> lines = new ArrayList<>();
        lines.add("Протокол проверки пакета " + protocol.packageId() + ", версия " + protocol.version());
        lines.add("Статус: " + protocol.status() + "; комплектность " + protocol.completeness().path("percent").asInt()
                + "%; нарушений " + protocol.failed() + "; предупреждений " + protocol.warnings()
                + "; на проверку " + protocol.needsReview());
        lines.add("Программа " + protocol.programCode() + " v" + protocol.programVersion() + ", правила "
                + protocol.rulesVersion() + ", создан " + protocol.createdAt());
        for (JsonNode missing : protocol.completeness().path("missing")) {
            lines.add("Не хватает: " + missing.path("type").asText() + " — " + missing.path("reason").asText());
        }
        return lines;
    }

    private static String[] cells(ProtocolView.Result result) {
        return new String[] {result.rule(), result.status(), nvl(result.message()), nvl(result.expected()),
                nvl(result.actual())};
    }

    private static String nvl(String value) {
        return value == null ? DASH : value;
    }

    private static byte[] docx(ProtocolView protocol) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : summary(protocol)) {
                XWPFRun run = doc.createParagraph().createRun();
                run.setText(line);
            }
            XWPFTable table = doc.createTable(protocol.results().size() + 1, HEADER.length);
            for (int col = 0; col < HEADER.length; col++) {
                table.getRow(0).getCell(col).setText(HEADER[col]);
            }
            for (int row = 0; row < protocol.results().size(); row++) {
                String[] values = cells(protocol.results().get(row));
                for (int col = 0; col < values.length; col++) {
                    table.getRow(row + 1).getCell(col).setText(values[col]);
                }
            }
            XWPFParagraph tail = doc.createParagraph();
            tail.createRun().setText("Вердикт принимает специалист; протокол — объяснение проверок.");
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] xlsx(ProtocolView protocol) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Протокол");
            int rowNum = 0;
            for (String line : summary(protocol)) {
                sheet.createRow(rowNum++).createCell(0).setCellValue(line);
            }
            rowNum++;
            Row header = sheet.createRow(rowNum++);
            for (int col = 0; col < HEADER.length; col++) {
                header.createCell(col).setCellValue(HEADER[col]);
            }
            for (ProtocolView.Result result : protocol.results()) {
                Row row = sheet.createRow(rowNum++);
                String[] values = cells(result);
                for (int col = 0; col < values.length; col++) {
                    row.createCell(col).setCellValue(values[col]);
                }
            }
            for (int col = 0; col < WIDTHS.length; col++) {
                sheet.setColumnWidth(col, WIDTHS[col] * CHAR_WIDTH);
            }
            book.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] pdf(ProtocolView protocol) throws IOException {
        List<String> lines = new ArrayList<>(summary(protocol));
        lines.add("");
        for (ProtocolView.Result result : protocol.results()) {
            String[] values = cells(result);
            lines.addAll(wrap(values[COL_STATUS] + " · " + values[COL_RULE] + ": " + values[COL_MESSAGE]));
        }
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream regular = ProtocolExporter.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
            PDFont font = PDType0Font.load(doc, regular);
            PDRectangle size = PDRectangle.A4;
            int index = 0;
            while (index < lines.size()) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(font, index == 0 ? TITLE_SIZE : FONT_SIZE);
                    stream.setLeading(LEADING);
                    stream.newLineAtOffset(MARGIN, size.getHeight() - MARGIN);
                    float cursorY = size.getHeight() - MARGIN;
                    while (index < lines.size() && cursorY > MARGIN) {
                        stream.showText(lines.get(index));
                        stream.newLine();
                        stream.setFont(font, FONT_SIZE);
                        cursorY -= LEADING;
                        index++;
                    }
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        String rest = text.replace('\n', ' ');
        while (rest.length() > WRAP) {
            int cut = rest.lastIndexOf(' ', WRAP);
            if (cut <= 0) {
                cut = WRAP;
            }
            out.add(rest.substring(0, cut));
            rest = "    " + rest.substring(cut).strip();
        }
        out.add(rest);
        return out;
    }
}
