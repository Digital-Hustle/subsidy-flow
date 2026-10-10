package ru.subsidy.package_.staff;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * ES256-токены сотрудников ключом из {@code STAFF_TOKEN_SIGNING_KEY} (ADR-023, перенесено из «Проходной»). Ключ один на все
 * экземпляры package: токен проверяется везде и переживает рестарт.
 */
@Service
public class TokenIssuer {

    private static final int FIELD_BITS = 256;
    private static final BigInteger THREE = BigInteger.valueOf(3);
    private static final String EC = "EC";
    private static final String ROLES = "roles";
    private static final String ALGORITHM = "SHA256withECDSA";

    private final StaffSettings props;
    private final Clock clock;
    private final ECKey key;
    private final NimbusJwtEncoder encoder;

    public TokenIssuer(StaffSettings props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = parse(props.signingKey());
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    /**
     * Подписанный токен сотрудника: роли в {@code roles} — как у Keycloak, сервисы читают их одинаково.
     *
     * @param subject {@code sub}
     * @param roles   роли
     * @param ttl     срок жизни
     * @param claims  дополнительные claims
     * @return токен и момент истечения
     */
    public Issued issue(String subject, List<String> roles, Duration ttl, Map<String, Object> claims) {
        Instant now = clock.instant();
        // exp в JWT — целые секунды: отдаём клиенту тот же момент
        Instant expires = now.plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet.Builder set = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(subject)
                .audience(props.audiences())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expires)
                .claim(ROLES, roles);
        claims.forEach(set::claim);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(key.getKeyID()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, set.build())).getTokenValue();
        return new Issued(token, expires.toEpochMilli());
    }

    /**
     * Открытые ключи (содержимое {@code keys} JWKS).
     *
     * @return JWK без закрытой части
     */
    public List<Map<String, Object>> publicKeys() {
        return List.of(key.toPublicJWK().toJSONObject());
    }

    /**
     * Ключ из PKCS#8. Открытую часть вычисляем {@code Q = d·G}: Java кладёт в PKCS#8 только закрытый скаляр, openssl —
     * ещё и точку, поэтому полагаться на неё нельзя. Делается один раз при старте.
     *
     * @param base64 PKCS#8 DER в base64
     * @return пара ключей с {@code kid} = отпечаток
     * @throws IllegalStateException ключ не P-256
     */
    static ECKey parse(String base64) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(base64.strip());
            ECPrivateKey privateKey = (ECPrivateKey) KeyFactory.getInstance(EC).generatePrivate(new PKCS8EncodedKeySpec(der));
            ECParameterSpec params = privateKey.getParams();
            if (params.getCurve().getField().getFieldSize() != FIELD_BITS) {
                throw new IllegalStateException("STAFF_TOKEN_SIGNING_KEY: нужна кривая P-256");
            }
            ECPoint point = multiply(params, privateKey.getS());
            ECPublicKey publicKey = (ECPublicKey) KeyFactory.getInstance(EC).generatePublic(new ECPublicKeySpec(point, params));
            checkPair(privateKey, publicKey);
            ECKey key = new ECKey.Builder(Curve.P_256, publicKey).privateKey(privateKey).keyUse(KeyUse.SIGNATURE).build();
            return new ECKey.Builder(key).keyIDFromThumbprint().build();
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException | JOSEException error) {
            throw new IllegalStateException("STAFF_TOKEN_SIGNING_KEY: нужен ключ ECDSA P-256 в PKCS#8 base64", error);
        }
    }

    /**
     * {@code k·G} в аффинных координатах (double-and-add); пара затем проверяется подписью в {@link #checkPair}.
     *
     * @param params параметры кривой
     * @param scalar закрытый скаляр
     * @return открытая точка
     */
    private static ECPoint multiply(ECParameterSpec params, BigInteger scalar) {
        BigInteger prime = ((ECFieldFp) params.getCurve().getField()).getP();
        BigInteger coefA = params.getCurve().getA();
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = params.getGenerator();
        for (int bit = 0; bit < scalar.bitLength(); bit++) {
            if (scalar.testBit(bit)) {
                result = add(result, addend, prime, coefA);
            }
            addend = add(addend, addend, prime, coefA);
        }
        return result;
    }

    private static ECPoint add(ECPoint left, ECPoint right, BigInteger prime, BigInteger coefA) {
        if (ECPoint.POINT_INFINITY.equals(left) || ECPoint.POINT_INFINITY.equals(right)) {
            return ECPoint.POINT_INFINITY.equals(left) ? right : left;
        }
        boolean doubling = left.equals(right);
        if (!doubling && left.getAffineX().equals(right.getAffineX())) {
            return ECPoint.POINT_INFINITY;
        }
        BigInteger slope = doubling
                ? left.getAffineX().pow(2).multiply(THREE).add(coefA).multiply(left.getAffineY().shiftLeft(1).modInverse(prime))
                : right.getAffineY().subtract(left.getAffineY()).multiply(right.getAffineX().subtract(left.getAffineX()).modInverse(prime));
        slope = slope.mod(prime);
        BigInteger sumX = slope.pow(2).subtract(left.getAffineX()).subtract(right.getAffineX()).mod(prime);
        BigInteger sumY = slope.multiply(left.getAffineX().subtract(sumX)).subtract(left.getAffineY()).mod(prime);
        return new ECPoint(sumX, sumY);
    }

    private static void checkPair(ECPrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
        byte[] probe = "subsidy-key-probe".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(privateKey);
        signer.update(probe);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(probe);
        if (!verifier.verify(signature)) {
            throw new IllegalStateException("STAFF_TOKEN_SIGNING_KEY: открытая часть не соответствует закрытой");
        }
    }

    /**
     * Выпущенный токен.
     *
     * @param token     JWT
     * @param expiresAt истекает, Unix ms
     */
    public record Issued(String token, long expiresAt) {
    }
}
