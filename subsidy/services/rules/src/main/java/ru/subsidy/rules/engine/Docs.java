package ru.subsidy.rules.engine;

import java.util.Map;

/**
 * Виды документов и имена полей (docproc doc-types/registry.json) и их названия для текстов специалисту.
 */
public final class Docs {

    /** Заявление на субсидию. */
    public static final String APPLICATION = "APPLICATION";
    /** Согласие на обработку персональных данных. */
    public static final String PD_CONSENT = "PD_CONSENT";
    /** Сертификат соответствия. */
    public static final String CERTIFICATE = "CERTIFICATE";
    /** Презентация проекта. */
    public static final String PRESENTATION = "PRESENTATION";
    /** Обоснование затрат. */
    public static final String COST_JUSTIFICATION = "COST_JUSTIFICATION";
    /** Выписка из банка. */
    public static final String BANK_STATEMENT = "BANK_STATEMENT";
    /** Документ на недвижимое имущество. */
    public static final String PROPERTY_DOC = "PROPERTY_DOC";
    /** Гарантийное письмо. */
    public static final String GUARANTEE_LETTER = "GUARANTEE_LETTER";
    /** Декларация ГИМС. */
    public static final String GIMS_DECLARATION = "GIMS_DECLARATION";
    /** Документ вне перечня. */
    public static final String OTHER = "OTHER";

    /** Позиции (статьи расходов). */
    public static final String ITEMS = "items";
    /** Сумма строки. */
    public static final String AMOUNT = "amount";

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry(APPLICATION, "Заявление"),
            Map.entry(PD_CONSENT, "Согласие на обработку ПД"),
            Map.entry(CERTIFICATE, "Сертификат соответствия"),
            Map.entry(PRESENTATION, "Презентация проекта"),
            Map.entry(COST_JUSTIFICATION, "Обоснование затрат"),
            Map.entry(BANK_STATEMENT, "Выписка из банка"),
            Map.entry(PROPERTY_DOC, "Документ на недвижимость"),
            Map.entry(GUARANTEE_LETTER, "Гарантийное письмо"),
            Map.entry(GIMS_DECLARATION, "Декларация ГИМС"),
            Map.entry(OTHER, "Прочий документ"));

    private static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("orgName", "организация"),
            Map.entry("inn", "ИНН"),
            Map.entry("ogrn", "ОГРН"),
            Map.entry("projectName", "наименование проекта"),
            Map.entry("description", "описание"),
            Map.entry("requestedAmount", "запрашиваемая сумма"),
            Map.entry("totalCost", "общая сумма расходов"),
            Map.entry("ownFunds", "собственные средства"),
            Map.entry("date", "дата"),
            Map.entry("signerName", "подписант"),
            Map.entry("fullName", "ФИО"),
            Map.entry("passport", "паспортные данные"),
            Map.entry("number", "номер"),
            Map.entry("issuer", "орган сертификации"),
            Map.entry("issueDate", "дата выдачи"),
            Map.entry("validUntil", "срок действия"),
            Map.entry("bankName", "банк"),
            Map.entry("account", "номер счёта"),
            Map.entry("balance", "остаток"),
            Map.entry("cadastralNumber", "кадастровый номер"),
            Map.entry("area", "площадь"),
            Map.entry("purpose", "назначение"),
            Map.entry("address", "адрес"),
            Map.entry("guarantorName", "организация-гарант"),
            Map.entry(AMOUNT, "сумма"),
            Map.entry(ITEMS, "статьи расходов"));

    private Docs() {
    }

    /**
     * Название вида для текста специалисту.
     *
     * @param type код вида
     * @return «Заявление», «Выписка из банка», …
     */
    public static String title(String type) {
        return TITLES.getOrDefault(type, type == null ? "Документ" : type);
    }

    /**
     * Название поля для текста специалисту.
     *
     * @param field имя поля
     * @return «ИНН», «собственные средства», … или само имя
     */
    public static String field(String field) {
        return FIELDS.getOrDefault(field, field);
    }

    /**
     * Известен ли вид.
     *
     * @param type код вида
     * @return true для видов из реестра
     */
    public static boolean known(String type) {
        return TITLES.containsKey(type);
    }

    /**
     * Только цифры.
     *
     * @param value строка
     * @return цифры
     */
    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
