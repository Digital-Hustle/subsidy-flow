package ru.subsidy.package_.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM для содержимого файлов в БД. Ключ — FILES_ENCRYPTION_KEY (base64, 32 байта).
 */
@Component
public class FileCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * Конструктор.
     *
     * @param base64Key ключ в base64
     * @throws IllegalStateException если ключ не 32 байта
     */
    public FileCrypto(@Value("${FILES_ENCRYPTION_KEY}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("FILES_ENCRYPTION_KEY должен быть 32 байта в base64");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /**
     * Шифрует содержимое со случайным nonce; aad привязывает шифротекст к файлу.
     *
     * @param plain содержимое
     * @param aad дополнительные данные (fileId:version)
     * @return шифротекст и nonce
     */
    public Sealed encrypt(byte[] plain, byte[] aad) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        return new Sealed(run(Cipher.ENCRYPT_MODE, plain, nonce, aad), nonce);
    }

    /**
     * Расшифровывает; изменённый шифротекст или чужой aad — исключение.
     *
     * @param sealed шифротекст и nonce
     * @param aad дополнительные данные
     * @return содержимое
     */
    public byte[] decrypt(Sealed sealed, byte[] aad) {
        return run(Cipher.DECRYPT_MODE, sealed.content(), sealed.nonce(), aad);
    }

    /**
     * AAD для шифротекста версии файла.
     *
     * @param fileId файл
     * @param version версия
     * @return байты fileId:version
     */
    public static byte[] aad(UUID fileId, int version) {
        return (fileId + ":" + version).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] run(int mode, byte[] input, byte[] nonce, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Ошибка шифрования файла", failure);
        }
    }

    /**
     * Шифротекст.
     *
     * @param content шифротекст с тегом
     * @param nonce nonce
     */
    public record Sealed(byte[] content, byte[] nonce) {
    }
}
