package ru.subsidy.rules.engine.declarative;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.money.Money;

/**
 * Денежное выражение правила: «ВИД.поле» или «ВИД.поле − ВИД.поле» / «+». Берётся первый документ вида.
 *
 * @param text исходный текст выражения
 * @param left первое слагаемое
 * @param sign 0 — одно поле, 1 — сумма, −1 — разность
 * @param right второе слагаемое или null
 */
record Operand(String text, Ref left, int sign, Ref right) {

    private static final int RIGHT = 3;
    private static final Pattern REF = Pattern.compile("([A-Z_]+)\\.([A-Za-z]+)");
    private static final Pattern EXPR = Pattern.compile("\\s*([A-Z_]+\\.[A-Za-z]+)\\s*(?:([+-])\\s*([A-Z_]+\\.[A-Za-z]+)\\s*)?");

    /**
     * Разбирает выражение.
     *
     * @param spec правило (для текста ошибки)
     * @param text выражение
     * @return операнд
     */
    static Operand parse(RuleSpec spec, String text) {
        Matcher expr = EXPR.matcher(text);
        if (!expr.matches()) {
            throw Params.invalid(spec, "выражение «" + text + "» — нужно ВИД.поле или ВИД.поле - ВИД.поле");
        }
        Ref first = ref(spec, expr.group(1));
        if (expr.group(2) == null) {
            return new Operand(text.strip(), first, 0, null);
        }
        return new Operand(text.strip(), first, "+".equals(expr.group(2)) ? 1 : -1, ref(spec, expr.group(RIGHT)));
    }

    /**
     * Разбирает список альтернатив: используется первая, для которой есть данные.
     *
     * @param spec правило
     * @param key параметр
     * @return операнды
     */
    static List<Operand> list(RuleSpec spec, String key) {
        List<Operand> out = new ArrayList<>();
        for (String text : Params.list(spec, key)) {
            out.add(parse(spec, text));
        }
        return out;
    }

    /**
     * Первая альтернатива, для которой есть данные.
     *
     * @param operands альтернативы
     * @param scope факты
     * @return значение или пусто
     */
    static Optional<Value> first(List<Operand> operands, Scope scope) {
        return operands.stream().map(operand -> operand.resolve(scope)).flatMap(Optional::stream).findFirst();
    }

    /**
     * Значение выражения по фактам пакета.
     *
     * @param scope факты
     * @return значение или пусто, если поля нет
     */
    Optional<Value> resolve(Scope scope) {
        Optional<Value> head = left.resolve(scope);
        if (right == null || head.isEmpty()) {
            return head;
        }
        Optional<Value> tail = right.resolve(scope);
        if (tail.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal amount = sign > 0 ? head.get().amount().add(tail.get().amount())
                : head.get().amount().subtract(tail.get().amount());
        List<Evidence> where = new ArrayList<>(head.get().evidence());
        where.addAll(tail.get().evidence());
        String label = head.get().label() + (sign > 0 ? " + " : " − ") + tail.get().label();
        return Optional.of(new Value(Money.round(amount), label, where));
    }

    private static Ref ref(RuleSpec spec, String text) {
        Matcher match = REF.matcher(text);
        if (!match.matches()) {
            throw Params.invalid(spec, "ссылка «" + text + "»");
        }
        Params.requireType(spec, match.group(1));
        return new Ref(match.group(1), match.group(2));
    }

    /**
     * Ссылка на поле первого документа вида.
     *
     * @param type вид
     * @param field поле
     */
    record Ref(String type, String field) {

        /**
         * Сумма поля.
         *
         * @param scope факты
         * @return значение или пусто
         */
        Optional<Value> resolve(Scope scope) {
            DocFacts doc = scope.first(type);
            if (doc == null) {
                return Optional.empty();
            }
            return doc.money(field)
                    .map(amount -> new Value(amount, Texts.where(doc, field), List.of(Evidence.of(doc, field))));
        }

        /**
         * Подпись для текста «нет данных».
         *
         * @return «Обоснование затрат: собственные средства»
         */
        String label() {
            return Docs.title(type) + ": " + Docs.field(field);
        }
    }

    /**
     * Вычисленное значение.
     *
     * @param amount сумма
     * @param label откуда взято
     * @param evidence ссылки
     */
    record Value(BigDecimal amount, String label, List<Evidence> evidence) {
    }
}
