package ru.subsidy.package_.applicant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Ключ заявки и код возобновления: случайные значения, в БД — только SHA-256.
 */
@Component
public class ApplicantKeys {

    private static final int KEY_BYTES = 32;
    private static final int RESUME_LENGTH = 8;
    private static final String RESUME_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();

    /**
     * Новый ключ доступа к заявке.
     *
     * @return ключ (base64url)
     */
    public String newKey() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Новый код возобновления.
     *
     * @return код из 8 символов без похожих букв и цифр
     */
    public String newResumeCode() {
        StringBuilder code = new StringBuilder(RESUME_LENGTH);
        for (int index = 0; index < RESUME_LENGTH; index++) {
            code.append(RESUME_ALPHABET.charAt(random.nextInt(RESUME_ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Хэш ключа доступа.
     *
     * @param key ключ
     * @return SHA-256 hex
     */
    public String keyHash(String key) {
        return sha256(key);
    }

    /**
     * Хэш кода возобновления, привязанный к заявке.
     *
     * @param applicationId заявка
     * @param code код (регистр не важен)
     * @return SHA-256 hex
     */
    public String resumeHash(UUID applicationId, String code) {
        return sha256(applicationId + ":" + code.strip().toUpperCase(Locale.ROOT));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
