package ru.subsidy.package_.organization;

/**
 * Проверка ИНН по контрольной сумме (FR-01): 10 цифр — юрлицо, 12 — ИП.
 */
public final class InnValidator {

    private static final int LEGAL_LENGTH = 10;
    private static final int PERSON_LENGTH = 12;
    private static final int MODULO = 11;
    private static final int RADIX = 10;
    private static final int[] LEGAL = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] PERSON_FIRST = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] PERSON_SECOND = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    private InnValidator() {
    }

    /**
     * Проверяет ИНН.
     *
     * @param inn строка ИНН
     * @return true, если длина и контрольные цифры верны
     */
    public static boolean isValid(String inn) {
        boolean digits = inn != null && inn.chars().allMatch(Character::isDigit);
        boolean valid = false;
        if (digits && inn.length() == LEGAL_LENGTH) {
            valid = checkDigit(inn, LEGAL) == digit(inn, LEGAL.length);
        } else if (digits && inn.length() == PERSON_LENGTH) {
            valid = checkDigit(inn, PERSON_FIRST) == digit(inn, PERSON_FIRST.length)
                    && checkDigit(inn, PERSON_SECOND) == digit(inn, PERSON_SECOND.length);
        }
        return valid;
    }

    private static int checkDigit(String inn, int[] weights) {
        int sum = 0;
        for (int idx = 0; idx < weights.length; idx++) {
            sum += weights[idx] * digit(inn, idx);
        }
        return sum % MODULO % RADIX;
    }

    private static int digit(String inn, int idx) {
        return Character.digit(inn.charAt(idx), RADIX);
    }
}
