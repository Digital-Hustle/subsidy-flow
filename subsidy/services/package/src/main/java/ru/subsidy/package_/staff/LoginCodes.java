package ru.subsidy.package_.staff;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Коды из письма и refresh-токены: генерация и хэши. Коды хранятся только как HMAC-SHA256(pepper, salt|code).
 */
@Component
public class LoginCodes {

    private static final int CODE_RANGE = 1_000_000;
    private static final int SALT_BYTES = 16;
    private static final int TOKEN_BYTES = 32;
    private static final String HMAC = "HmacSHA256";
    private static final String HIDDEN = "•••••";
    private static final int SHOWN = 3;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public LoginCodes(StaffSettings props) {
        this.key = new SecretKeySpec(("login|" + props.pepper()).getBytes(StandardCharsets.UTF_8), HMAC);
    }

    /**
     * Шесть цифр.
     *
     * @return код
     */
    public String code() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(CODE_RANGE));
    }

    /**
     * Случайная соль или токен.
     *
     * @param refresh {@code true} — refresh-токен (32 байта), иначе соль (16 байт)
     * @return base64url
     */
    public String randomToken(boolean refresh) {
        byte[] bytes = new byte[refresh ? TOKEN_BYTES : SALT_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * HMAC кода с солью.
     *
     * @param salt соль
     * @param code код (пробелы и дефисы отбрасываются)
     * @return base64url
     */
    public String hash(String salt, String code) {
        return mac(salt + "|" + code.replaceAll("[\\s-]", ""));
    }

    /**
     * HMAC refresh-токена.
     *
     * @param token токен
     * @return base64url
     */
    public String hashToken(String token) {
        return mac("refresh|" + token);
    }

    /**
     * Сравнение за постоянное время.
     *
     * @param expected ожидаемый хэш
     * @param actual   полученный
     * @return равны
     */
    public static boolean same(String expected, String actual) {
        return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Маска адреса для ответа: {@code ann•••••@example.ru}.
     *
     * @param email адрес
     * @return маска
     */
    public static String mask(String email) {
        int atSign = email.indexOf('@');
        if (atSign < 1) {
            return HIDDEN;
        }
        String local = email.substring(0, atSign);
        String shown = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, Math.min(SHOWN, local.length() - 1));
        return shown + HIDDEN + email.substring(atSign);
    }

    private String mac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }
}
