package ru.subsidy.package_.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import ru.subsidy.package_.storage.FileCrypto.Sealed;

class FileCryptoTest {

    private static final int KEY_BYTES = 32;
    private static final byte[] AAD = "file:1".getBytes(StandardCharsets.UTF_8);

    private static FileCrypto crypto() {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return new FileCrypto(Base64.getEncoder().encodeToString(key));
    }

    @Test
    void roundTrip_andNonceIsRandom() {
        FileCrypto crypto = crypto();
        byte[] plain = "выписка ЕГРЮЛ".getBytes(StandardCharsets.UTF_8);
        Sealed first = crypto.encrypt(plain, AAD);
        Sealed second = crypto.encrypt(plain, AAD);
        assertThat(first.content()).isNotEqualTo(plain);
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(crypto.decrypt(first, AAD)).isEqualTo(plain);
    }

    @Test
    void tamperedOrForeignAad_fails() {
        FileCrypto crypto = crypto();
        Sealed sealed = crypto.encrypt(new byte[]{1, 2, 3}, AAD);
        sealed.content()[0] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(sealed, AAD)).isInstanceOf(IllegalStateException.class);
        Sealed fresh = crypto.encrypt(new byte[]{1, 2, 3}, AAD);
        byte[] other = "file:2".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> crypto.decrypt(fresh, other)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyLength_rejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> new FileCrypto(shortKey)).isInstanceOf(IllegalStateException.class);
    }
}
