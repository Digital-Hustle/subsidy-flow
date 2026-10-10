package ru.subsidy.package_.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import ru.subsidy.common.security.IssuerJwtDecoder;
import ru.subsidy.package_.config.StaffKeys;

/**
 * Два издателя (ADR-023): токен сотрудника проверяется ключом package и не может выдать себя за service account.
 */
class IssuerJwtDecoderTest {

    private static final String ISSUER = "https://subsidy.test/package";
    private static final List<String> AUDIENCES = List.of("package", "docproc", "rules", "assistant");

    private final StaffSettings settings = new StaffSettings(false, Duration.ofMinutes(10), Duration.ofMinutes(1), 5,
            Duration.ofHours(12), Duration.ofMinutes(5), "pepper", ISSUER, AUDIENCES, StaffKeys.pkcs8(), "", "no-reply@test", "", false);
    private final TokenIssuer issuer = new TokenIssuer(settings, Clock.systemUTC());

    private JwtDecoder staffDecoder() throws Exception {
        ECKey key = (ECKey) JWK.parse(issuer.publicKeys().getFirst());
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, new ImmutableJWKSet<>(new JWKSet(key))));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<Object>("azp", azp -> azp == null)));
        return decoder;
    }

    @Test
    void staffToken_decodedByStaffKey_withRoles() throws Exception {
        // given
        String token = issuer.issue("u1", List.of("SPECIALIST"), Duration.ofMinutes(5), Map.of("name", "Анна")).token();
        JwtDecoder keycloak = value -> {
            throw new AssertionError("токен сотрудника не должен уходить в Keycloak");
        };

        // when
        Jwt jwt = new IssuerJwtDecoder(ISSUER, staffDecoder(), keycloak).decode(token);

        // then
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SPECIALIST");
        assertThat(jwt.getAudience()).containsAll(AUDIENCES);
    }

    @Test
    void staffToken_withAzp_rejected_otherIssuer_goesToKeycloak() throws Exception {
        // given
        String forged = issuer.issue("u1", List.of("SPECIALIST"), Duration.ofMinutes(5), Map.of("azp", "svc-docproc")).token();
        StaffSettings other = new StaffSettings(false, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, Duration.ofHours(12),
                Duration.ofMinutes(5), "pepper", "https://keycloak.test/realms/subsidy", AUDIENCES, StaffKeys.pkcs8(), "", "x@test", "",
                false);
        String foreign = new TokenIssuer(other, Clock.systemUTC()).issue("u2", List.of("AUDITOR"), Duration.ofMinutes(5), Map.of()).token();
        JwtDecoder keycloak = value -> {
            throw new JwtException("keycloak");
        };
        IssuerJwtDecoder decoder = new IssuerJwtDecoder(ISSUER, staffDecoder(), keycloak);

        // when / then
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(foreign)).hasMessage("keycloak");
    }
}
