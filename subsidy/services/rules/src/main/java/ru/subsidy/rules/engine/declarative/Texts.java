package ru.subsidy.rules.engine.declarative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.facts.DocFacts;

/**
 * Тексты замечаний: даты, проценты, ссылки на документы.
 */
final class Texts {

    /** Разделитель перечислений. */
    static final String SEMI = "; ";
    /** Пробел. */
    static final String SPACE = " ";
    /** Разделитель «документ — значение». */
    static final String DASH = " — ";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Texts() {
    }

    /**
     * Дата по-русски.
     *
     * @param date дата
     * @return «01.03.2026»
     */
    static String date(LocalDate date) {
        return DATE.format(date);
    }

    /**
     * Доля в процентах.
     *
     * @param ratio доля (0.5)
     * @return «50,00 %»
     */
    static String percent(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " %";
    }

    /**
     * «Заявление: ИНН».
     *
     * @param doc документ
     * @param field поле
     * @return подпись
     */
    static String where(DocFacts doc, String field) {
        return Docs.title(doc.type()) + ": " + Docs.field(field);
    }

    /**
     * Ссылки на поле в документах.
     *
     * @param docs документы
     * @param field поле
     * @return evidence
     */
    static List<Evidence> evidence(List<DocFacts> docs, String field) {
        return docs.stream().map(doc -> Evidence.of(doc, field)).toList();
    }

    /**
     * Названия полей через запятую.
     *
     * @param fields поля
     * @return «ИНН, дата»
     */
    static String fields(List<String> fields) {
        return fields.stream().map(Docs::field).collect(Collectors.joining(", "));
    }
}
