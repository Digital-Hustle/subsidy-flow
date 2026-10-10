package ru.subsidy.package_.staff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;

/**
 * Вход сотрудника по коду из письма (FR-60, ADR-023, как PPL-13 «Проходной»): почта → 6 цифр → сессия. Access-токен
 * короткий: отключённый сотрудник теряет доступ к сервисам не позже чем через {@code accessTtl}.
 * На демо-стенде код возвращается в ответе, а {@link StaffSettings#DEMO_CODE} пускает любого активного сотрудника.
 */
@Service
public class StaffLoginService {

    private static final String INVALID = "Неверный код";
    private static final String ASK_NEW = INVALID + " — запросите новый";

    private final LoginCodeRepository codes;
    private final StaffRepository staff;
    private final SessionRepository sessions;
    private final LoginCodes crypto;
    private final StaffMailer mailer;
    private final TokenIssuer issuer;
    private final StaffSettings props;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param codes    коды
     * @param staff    сотрудники
     * @param sessions сессии
     * @param crypto   коды и хэши
     * @param mailer   письма
     * @param issuer   токены
     * @param props    настройки
     * @param clock    часы
     */
    public StaffLoginService(LoginCodeRepository codes, StaffRepository staff, SessionRepository sessions, LoginCodes crypto,
                             StaffMailer mailer, TokenIssuer issuer, StaffSettings props, Clock clock) {
        this.codes = codes;
        this.staff = staff;
        this.sessions = sessions;
        this.crypto = crypto;
        this.mailer = mailer;
        this.issuer = issuer;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Отправляет код. Ответ одинаковый для любого адреса; письмо — только активным и приглашённым.
     *
     * @param rawEmail адрес
     * @return маска адреса и сроки (на демо-стенде — и сам код)
     * @throws DomainException LOGIN_RESEND_TOO_SOON раньше {@code resendAfter}; письмо уходит в фоне после коммита (StaffMailer)
     */
    @Transactional
    public CodeSent requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();
        codes.lock(email).filter(row -> now.isBefore(row.resendAt())).ifPresent(row -> {
            long wait = Math.max(1, Duration.between(now, row.resendAt()).toSeconds());
            throw new DomainException(ErrorCode.LOGIN_RESEND_TOO_SOON, "Повторно отправить код можно через " + wait + " с");
        });
        StaffUser user = staff.lockByEmail(email).filter(found -> found.status() != StaffStatus.DISABLED).orElse(null);
        String salt = crypto.randomToken(false);
        String code = crypto.code();
        Instant expires = now.plus(props.codeTtl());
        Instant resend = now.plus(props.resendAfter());
        codes.put(email, user != null ? crypto.hash(salt, code) : null, salt, expires, resend);
        // демо-сотрудникам письма не шлём: их адреса вымышленные, код виден на странице входа
        if (user != null && !user.demo()) {
            mailer.loginCode(email, user.name(), code, props.codeTtl().toMinutes());
        }
        // демо: код виден в интерфейсе («письмо» стенда); для неизвестного адреса кода нет — ответ ничего не раскрывает
        String demoCode = props.demo() && user != null ? code : null;
        return new CodeSent(LoginCodes.mask(email), expires.toEpochMilli(), resend.toEpochMilli(), demoCode);
    }

    /**
     * Проверяет код и открывает сессию. Первый вход приглашённого делает его активным.
     *
     * @param rawEmail адрес
     * @param code     код из письма
     * @return сессия
     * @throws DomainException LOGIN_CODE_INVALID, LOGIN_CODE_EXPIRED, LOGIN_CODE_LOCKED
     */
    @Transactional(noRollbackFor = DomainException.class)
    public StaffSession verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();
        boolean demoCode = props.demo() && StaffSettings.DEMO_CODE.equals(code.replaceAll("[\\s-]", ""));
        if (!demoCode) {
            checkCode(email, code, now);
        }
        codes.delete(email);
        StaffUser user = staff.lockByEmail(email).filter(found -> found.status() != StaffStatus.DISABLED)
                .orElseThrow(() -> new DomainException(ErrorCode.LOGIN_CODE_INVALID, ASK_NEW));
        if (user.status() == StaffStatus.INVITED) {
            staff.setStatus(user.id(), StaffStatus.ACTIVE);
            user = user.withStatus(StaffStatus.ACTIVE);
        }
        staff.touch(user.id(), now);
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        String refresh = crypto.randomToken(true);
        Instant sessionEnd = now.plus(props.sessionTtl());
        sessions.insert(sessionId, user.id(), crypto.hashToken(refresh), now, sessionEnd);
        return session(user, sessionId, refresh, sessionEnd, now);
    }

    /**
     * Продлевает access-токен; refresh одноразовый (ротация). Роль берётся текущая — смена роли доходит без перевхода.
     *
     * @param refreshToken refresh из cookie
     * @return новая пара токенов
     * @throws DomainException SESSION_EXPIRED — сессия закрыта, истекла или сотрудник отключён
     */
    @Transactional
    public StaffSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw expired();
        }
        Instant now = clock.instant();
        SessionRepository.SessionRow row = sessions.lockByRefresh(crypto.hashToken(refreshToken), now)
                .orElseThrow(StaffLoginService::expired);
        StaffUser user = staff.lock(row.staffId()).filter(found -> found.status() == StaffStatus.ACTIVE).orElse(null);
        if (user == null) {
            sessions.revoke(row.id(), now);
            throw expired();
        }
        String refresh = crypto.randomToken(true);
        sessions.rotate(row.id(), crypto.hashToken(refresh));
        staff.touch(user.id(), now);
        return session(user, row.id(), refresh, row.expiresAt(), now);
    }

    /**
     * Закрывает сессию по refresh (выход).
     *
     * @param refreshToken refresh из cookie или null
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        sessions.lockByRefresh(crypto.hashToken(refreshToken), now).ifPresent(row -> sessions.revoke(row.id(), now));
    }

    private void checkCode(String email, String code, Instant now) {
        LoginCodeRepository.CodeRow row = codes.lock(email)
                .orElseThrow(() -> new DomainException(ErrorCode.LOGIN_CODE_INVALID, ASK_NEW));
        if (row.attempts() >= props.maxAttempts()) {
            throw locked();
        }
        if (!now.isBefore(row.expiresAt())) {
            throw new DomainException(ErrorCode.LOGIN_CODE_EXPIRED, "Код устарел — запросите новый");
        }
        if (!LoginCodes.same(row.codeHash(), crypto.hash(row.salt(), code))) {
            int left = props.maxAttempts() - codes.failed(email);
            if (left <= 0) {
                throw locked();
            }
            throw new DomainException(ErrorCode.LOGIN_CODE_INVALID, INVALID + ". Осталось попыток: " + left);
        }
    }

    private StaffSession session(StaffUser user, UUID sessionId, String refresh, Instant sessionEnd, Instant now) {
        Duration ttl = Duration.between(now, sessionEnd);
        if (ttl.compareTo(props.accessTtl()) > 0) {
            ttl = props.accessTtl();
        }
        TokenIssuer.Issued token = issuer.issue(user.id().toString(), List.of(user.role().name()), ttl,
                Map.of("name", user.name(), "email", user.email(), "preferred_username", user.email(), "sid", sessionId.toString()));
        return new StaffSession(token.token(), refresh, token.expiresAt(), sessionEnd.toEpochMilli(), user);
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private DomainException locked() {
        return new DomainException(ErrorCode.LOGIN_CODE_LOCKED, "Код введён неверно " + props.maxAttempts() + " раз — запросите новый");
    }

    private static DomainException expired() {
        return new DomainException(ErrorCode.SESSION_EXPIRED, "Сессия завершена — войдите снова");
    }
}
