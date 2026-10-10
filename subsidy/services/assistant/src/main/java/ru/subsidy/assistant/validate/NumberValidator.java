package ru.subsidy.assistant.validate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Каждое число и дата в ответе должны встречаться в результатах инструментов или в вопросе (B-06).
 * Форматы нормализуются: «1 500 000,00» = «1500000.00» = 1500000; «01.03.2026» = «2026-03-01».
 * Целые 0…10 не проверяются (нумерация списков, счётчики в тексте).
 */
@Component
public class NumberValidator {

    private static final String SPACE = " ";
    private static final Pattern RU_DATE = Pattern.compile("\\b(\\d{2})\\.(\\d{2})\\.(\\d{4})\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern NUMBER = Pattern.compile(
            "\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?");
    private static final BigDecimal TRIVIAL = BigDecimal.TEN;
    private static final String DASH = "-";
    private static final String DATE_PREFIX = "date:";
    private static final int FIRST = 1;
    private static final int SECOND = 2;
    private static final int THIRD = 3;

    /**
     * Сверяет ответ с данными.
     *
     * @param answer ответ модели
     * @param question вопрос пользователя
     * @param toolResults результаты инструментов
     * @return статус и неподтверждённые значения
     */
    public Validation validate(String answer, String question, List<JsonNode> toolResults) {
        Set<String> known = new HashSet<>(tokens(question == null ? "" : question));
        toolResults.forEach(result -> collect(result, known));
        List<String> unknown = new ArrayList<>();
        for (String token : new LinkedHashSet<>(tokens(answer == null ? "" : answer))) {
            if (!known.contains(token) && !trivial(token)) {
                unknown.add(token.startsWith(DATE_PREFIX) ? token.substring(DATE_PREFIX.length()) : token);
            }
        }
        return new Validation(unknown.isEmpty() ? Validation.OK : Validation.MISMATCH, unknown);
    }

    private static void collect(JsonNode node, Set<String> known) {
        if (node.isNumber()) {
            known.add(normalize(node.decimalValue()));
        } else if (node.isTextual()) {
            known.addAll(tokens(node.asText()));
        } else {
            node.forEach(child -> collect(child, known));
        }
    }

    /**
     * Числа и даты текста в нормализованном виде.
     *
     * @param text текст
     * @return токены: числа без лишних нулей, даты как «date:YYYY-MM-DD»
     */
    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder rest = new StringBuilder();
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            out.add(DATE_PREFIX + iso.group(FIRST) + DASH + iso.group(SECOND) + DASH + iso.group(THIRD));
            iso.appendReplacement(rest, SPACE);
        }
        iso.appendTail(rest);
        String withoutIso = rest.toString();
        rest.setLength(0);
        Matcher ruDate = RU_DATE.matcher(withoutIso);
        while (ruDate.find()) {
            out.add(DATE_PREFIX + ruDate.group(THIRD) + DASH + ruDate.group(SECOND) + DASH + ruDate.group(FIRST));
            ruDate.appendReplacement(rest, SPACE);
        }
        ruDate.appendTail(rest);
        Matcher number = NUMBER.matcher(rest);
        while (number.find()) {
            String raw = number.group().replaceAll("[ \\u00a0\\u202f]", "").replace(',', '.');
            out.add(normalize(new BigDecimal(raw)));
        }
        return out;
    }

    private static String normalize(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static boolean trivial(String token) {
        return !token.startsWith(DATE_PREFIX) && token.indexOf('.') < 0
                && new BigDecimal(token).compareTo(TRIVIAL) <= 0;
    }
}
