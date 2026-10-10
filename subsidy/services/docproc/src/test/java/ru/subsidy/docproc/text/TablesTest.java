package ru.subsidy.docproc.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class TablesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode page(String tables) throws Exception {
        return JSON.readTree("{\"n\":2,\"text\":\"Смета\",\"tables\":" + tables + "}");
    }

    @Test
    void rendersRowsWithColumnSeparators() throws Exception {
        String text = Tables.render(page("[{\"rows\":[[\"Наименование\",\"Кол-во\",\"Сумма\"],"
                + "[\"Станок | ЧПУ\",\"2\",\"300000.00\"],[\"Монтаж\",\"\",\"45000.00\"]],\"bbox\":[0,0,1,1]}]"));
        assertThat(text).isEqualTo("[Таблица 1, стр. 2]\n| Наименование | Кол-во | Сумма |\n"
                + "| Станок / ЧПУ | 2 | 300000.00 |\n| Монтаж |  | 45000.00 |");
    }

    @Test
    void noTables_emptyAndTextUnchanged() throws Exception {
        assertThat(Tables.render(page("[]"))).isEmpty();
        PageText plain = new PageText(1, "Счёт", PageText.OCR).withTables("");
        assertThat(plain.tables()).isNull();
        assertThat(plain.textWithTables()).isEqualTo("Счёт");
    }

    @Test
    void tablesAppendedForExtractionOnly() {
        PageText page = new PageText(1, "Счёт", PageText.TEXT_LAYER).withTables("[Таблица 1, стр. 1]\n| a | b |");
        assertThat(page.text()).isEqualTo("Счёт");
        assertThat(page.textWithTables()).isEqualTo("Счёт\n\n[Таблица 1, стр. 1]\n| a | b |");
    }
}
