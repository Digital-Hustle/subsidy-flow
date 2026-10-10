package ru.subsidy.docproc.text;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Таблицы страницы из ocr ({@code pages[].tables}, OCR-06) → текст для модели при извлечении полей (DP-12).
 * Строки таблицы — «| ячейка | ячейка |», чтобы модель не путала колонки «кол-во / цена / сумма».
 */
public final class Tables {

    private static final String BAR = " | ";
    private static final String EDGE = "|";

    private Tables() {
    }

    /**
     * Текст таблиц страницы.
     *
     * @param page страница ответа ocr
     * @return текст или пустая строка, если таблиц нет
     */
    public static String render(JsonNode page) {
        List<String> blocks = new ArrayList<>();
        int index = 0;
        for (JsonNode table : page.path("tables")) {
            index++;
            StringBuilder out = new StringBuilder("[Таблица ").append(index).append(", стр. ")
                    .append(page.path("n").asInt()).append("]");
            for (JsonNode row : table.path("rows")) {
                List<String> cells = new ArrayList<>();
                row.forEach(cell -> cells.add(cell.asText("").replace(EDGE, "/").strip()));
                out.append('\n').append(EDGE).append(' ').append(String.join(BAR, cells)).append(' ').append(EDGE);
            }
            blocks.add(out.toString());
        }
        return String.join("\n\n", blocks);
    }
}
