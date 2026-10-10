package ru.subsidy.package_.staff;

import java.time.Duration;
import java.util.List;

/**
 * Настройки входа сотрудников (ADR-023).
 *
 * @param demo          демо-стенд ({@code STAND_MODE=demo}): демо-сотрудники, код в ответе и общий код {@link #DEMO_CODE}
 * @param codeTtl       срок кода из письма
 * @param resendAfter   повторная отправка не раньше
 * @param maxAttempts   неверных попыток на код
 * @param sessionTtl    срок сессии (refresh)
 * @param accessTtl     срок access-токена
 * @param pepper        секрет HMAC кодов и refresh
 * @param issuer        issuer токенов сотрудников
 * @param audiences     сервисы, которым адресован токен
 * @param signingKey    ключ подписи ES256 (PKCS#8 base64)
 * @param bootstrap     почта первого старшего специалиста (боевой стенд без сотрудников)
 * @param mailFrom      адрес отправителя
 * @param loginUrl      адрес входа для писем
 * @param secureCookie  cookie сессии только по HTTPS
 */
public record StaffSettings(boolean demo, Duration codeTtl, Duration resendAfter, int maxAttempts, Duration sessionTtl,
                            Duration accessTtl, String pepper, String issuer, List<String> audiences, String signingKey,
                            String bootstrap, String mailFrom, String loginUrl, boolean secureCookie) {

    /** Общий код демо-стенда: пускает любого активного сотрудника. На боевом стенде не принимается. */
    public static final String DEMO_CODE = "000000";
}
