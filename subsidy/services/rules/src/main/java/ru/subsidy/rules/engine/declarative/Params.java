package ru.subsidy.rules.engine.declarative;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Docs;

/**
 * Чтение и проверка параметров правила с понятными ошибками для администратора.
 */
final class Params {

    /** Параметр видов документов. */
    static final String TYPES = "types";
    /** Параметр поля. */
    static final String FIELD = "field";
    /** Параметр списка полей. */
    static final String FIELDS = "fields";

    private static final String RULE = "Правило ";
    private static final String COLON = ": ";

    private Params() {
    }

    /**
     * Ошибка параметров правила для администратора.
     *
     * @param spec правило
     * @param detail что не так
     * @return исключение «Правило КОД: …»
     */
    static IllegalArgumentException invalid(RuleSpec spec, String detail) {
        return new IllegalArgumentException(RULE + spec.code() + COLON + detail);
    }

    /**
     * Виды документов правила (обязательный непустой список известных видов или «*»).
     *
     * @param spec правило
     * @return виды
     * @throws IllegalArgumentException параметр задан неверно
     */
    static List<String> types(RuleSpec spec) {
        List<String> types = strings(spec.params().path(TYPES));
        if (types.isEmpty()) {
            throw invalid(spec, "укажите виды документов (types)");
        }
        for (String type : types) {
            requireType(spec, type);
        }
        return types;
    }

    /**
     * Проверяет, что вид документа известен.
     *
     * @param spec правило
     * @param type вид
     * @throws IllegalArgumentException параметр задан неверно
     */
    static void requireType(RuleSpec spec, String type) {
        if (!Scope.ANY.equals(type) && !Docs.known(type)) {
            throw invalid(spec, "неизвестный вид документа " + type);
        }
    }

    /**
     * Обязательный текстовый параметр.
     *
     * @param spec правило
     * @param key имя
     * @return значение
     * @throws IllegalArgumentException параметр задан неверно
     */
    static String text(RuleSpec spec, String key) {
        String value = spec.params().path(key).asText("").strip();
        if (value.isEmpty()) {
            throw invalid(spec, "не задан параметр " + key);
        }
        return value;
    }

    /**
     * Текстовый параметр со значением по умолчанию.
     *
     * @param spec правило
     * @param key имя
     * @param fallback по умолчанию
     * @return значение
     */
    static String text(RuleSpec spec, String key, String fallback) {
        String value = spec.params().path(key).asText("").strip();
        return value.isEmpty() ? fallback : value;
    }

    /**
     * Неотрицательное целое со значением по умолчанию.
     *
     * @param spec правило
     * @param key имя
     * @param fallback по умолчанию
     * @return значение
     * @throws IllegalArgumentException параметр задан неверно
     */
    static int count(RuleSpec spec, String key, int fallback) {
        JsonNode node = spec.params().path(key);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (!node.canConvertToInt() || node.asInt() < 0) {
            throw invalid(spec, key + " — целое число не меньше 0");
        }
        return node.asInt();
    }

    /**
     * Десятичное число (строкой или числом), необязательное.
     *
     * @param spec правило
     * @param key имя
     * @return число или null
     * @throws IllegalArgumentException параметр задан неверно
     */
    static BigDecimal decimal(RuleSpec spec, String key) {
        JsonNode node = spec.params().path(key);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().strip().replace(',', '.'));
        } catch (NumberFormatException broken) {
            throw invalid(spec, key + " — число, например 0.5");
        }
    }

    /**
     * Регулярное выражение.
     *
     * @param spec правило
     * @param key имя
     * @return шаблон
     * @throws IllegalArgumentException параметр задан неверно
     */
    static Pattern pattern(RuleSpec spec, String key) {
        try {
            return Pattern.compile(text(spec, key));
        } catch (PatternSyntaxException broken) {
            throw invalid(spec, "неверный шаблон " + key);
        }
    }

    /**
     * Список строк (массив или одна строка).
     *
     * @param node узел
     * @return строки
     */
    static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isTextual() && !node.asText().isBlank()) {
            out.add(node.asText().strip());
        }
        node.forEach(item -> {
            if (!item.asText("").isBlank()) {
                out.add(item.asText().strip());
            }
        });
        return out;
    }

    /**
     * Обязательный непустой список строк.
     *
     * @param spec правило
     * @param key имя
     * @return строки
     * @throws IllegalArgumentException параметр задан неверно
     */
    static List<String> list(RuleSpec spec, String key) {
        List<String> out = strings(spec.params().path(key));
        if (out.isEmpty()) {
            throw invalid(spec, "не задан список " + key);
        }
        return out;
    }
}
