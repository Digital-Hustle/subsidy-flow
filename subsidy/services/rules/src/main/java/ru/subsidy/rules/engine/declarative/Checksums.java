package ru.subsidy.rules.engine.declarative;

/**
 * Контрольные разряды реквизитов (аналог Луна для российских номеров): ИНН 10/12 — взвешенная сумма mod 11 mod 10,
 * ОГРН 13 — остаток от деления на 11, ОГРНИП 15 — на 13 (последняя цифра остатка).
 */
final class Checksums {

    /** Код проверки ИНН. */
    static final String INN = "inn";
    /** Код проверки ОГРН/ОГРНИП. */
    static final String OGRN = "ogrn";

    private static final int[] INN10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int LEGAL = 10;
    private static final int PERSON = 12;
    private static final int OGRN_LEN = 13;
    private static final int OGRNIP_LEN = 15;
    private static final long MOD10 = 10;
    private static final long MOD11 = 11;
    private static final long MOD13 = 13;

    private Checksums() {
    }

    /**
     * Сходится ли контрольный разряд.
     *
     * @param kind inn | ogrn
     * @param digits только цифры
     * @return true, если контрольные цифры верны
     */
    static boolean valid(String kind, String digits) {
        return OGRN.equals(kind) ? ogrn(digits) : inn(digits);
    }

    private static boolean inn(String value) {
        boolean valid = false;
        if (value.length() == LEGAL) {
            valid = control(value, INN10) == digit(value, LEGAL - 1);
        } else if (value.length() == PERSON) {
            valid = control(value, INN12_11) == digit(value, PERSON - 2) && control(value, INN12_12) == digit(value, PERSON - 1);
        }
        return valid;
    }

    private static boolean ogrn(String value) {
        if (value.length() != OGRN_LEN && value.length() != OGRNIP_LEN) {
            return false;
        }
        long body = Long.parseLong(value.substring(0, value.length() - 1));
        long mod = body % (value.length() == OGRN_LEN ? MOD11 : MOD13);
        return mod % MOD10 == digit(value, value.length() - 1);
    }

    private static int control(String value, int[] weights) {
        int sum = 0;
        for (int idx = 0; idx < weights.length; idx++) {
            sum += weights[idx] * digit(value, idx);
        }
        return (int) (sum % MOD11 % MOD10);
    }

    private static int digit(String value, int idx) {
        return value.charAt(idx) - '0';
    }
}
