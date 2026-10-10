package ru.subsidy.package_.config;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Ключ подписи токенов сотрудников для тестов: свежий P-256 на каждый прогон.
 */
public final class StaffKeys {

    private StaffKeys() {
    }

    /**
     * Закрытый ключ ES256.
     *
     * @return PKCS#8 base64
     */
    public static String pkcs8() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }
}
