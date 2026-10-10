package ru.subsidy.common.security;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

import com.nimbusds.jwt.JWTParser;

/**
 * Два издателя токенов (ADR-023): Keycloak — service accounts сервисов, package — сессии сотрудников после входа
 * по коду из письма. Издатель выбирается по {@code iss}, подпись и срок проверяет декодер именно этого издателя.
 * Токен сотрудника не может нести {@code azp}: иначе он выдал бы себя за service account (SVC_*).
 */
public final class IssuerJwtDecoder implements JwtDecoder {

    private final String staffIssuer;
    private final JwtDecoder staff;
    private final JwtDecoder keycloak;

    /**
     * Конструктор.
     *
     * @param staffIssuer issuer сессий сотрудников
     * @param staff       декодер сессий сотрудников
     * @param keycloak    декодер Keycloak
     */
    public IssuerJwtDecoder(String staffIssuer, JwtDecoder staff, JwtDecoder keycloak) {
        this.staffIssuer = staffIssuer;
        this.staff = staff;
        this.keycloak = keycloak;
    }

    /**
     * Декодер из настроек сервиса.
     *
     * @param staffIssuer  issuer сессий сотрудников ({@code STAFF_TOKEN_ISSUER})
     * @param staffJwks    ключи сессий ({@code STAFF_JWKS_URI}, внутренняя сеть)
     * @param kcIssuer     issuer Keycloak или пусто
     * @param kcJwks       ключи Keycloak или пусто (тогда — discovery по issuer)
     * @param audiences    допустимые {@code aud} (пусто — не проверять)
     * @return декодер
     */
    public static IssuerJwtDecoder of(String staffIssuer, String staffJwks, String kcIssuer, String kcJwks, List<String> audiences) {
        // ES256 явно: по умолчанию декодер принимает только RS256 и отвечает «Unsupported algorithm of ES256»
        NimbusJwtDecoder staff = NimbusJwtDecoder.withJwkSetUri(staffJwks).jwsAlgorithm(SignatureAlgorithm.ES256).build();
        staff.setJwtValidator(validator(staffIssuer, audiences, true));
        Supplier<JwtDecoder> keycloak = () -> {
            NimbusJwtDecoder decoder = blank(kcJwks)
                    ? (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(kcIssuer)
                    : NimbusJwtDecoder.withJwkSetUri(kcJwks).build();
            decoder.setJwtValidator(validator(kcIssuer, audiences, false));
            return decoder;
        };
        return new IssuerJwtDecoder(staffIssuer, staff, new SupplierJwtDecoder(keycloak));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer;
        try {
            issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException error) {
            throw new BadJwtException("Токен не читается", error);
        }
        return staffIssuer.equals(issuer) ? staff.decode(token) : keycloak.decode(token);
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer, List<String> audiences, boolean noClient) {
        List<OAuth2TokenValidator<Jwt>> list = new ArrayList<>();
        list.add(blank(issuer) ? JwtValidators.createDefault() : JwtValidators.createDefaultWithIssuer(issuer));
        if (audiences != null && !audiences.isEmpty()) {
            list.add(new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                    aud -> aud != null && aud.stream().anyMatch(audiences::contains)));
        }
        if (noClient) {
            list.add(new JwtClaimValidator<Object>("azp", azp -> azp == null));
        }
        return new DelegatingOAuth2TokenValidator<>(list);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
