package ru.subsidy.package_.staff;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;

/**
 * Сотрудники и роли (FR-60, ADR-023): приглашение по почте, смена роли, отключение. Управляет старший специалист;
 * последнего старшего понизить или отключить нельзя — иначе управлять доступом станет некому.
 */
@Service
public class StaffService {

    private static final String SENIOR = "Старший специалист";
    private static final String NOT_FOUND = "Сотрудник не найден";
    /** Названия ролей для писем и интерфейса. */
    public static final Map<StaffRole, String> LABELS = Map.of(StaffRole.SPECIALIST, "Специалист",
            StaffRole.SENIOR_SPECIALIST, SENIOR, StaffRole.AUDITOR, "Аудитор");
    private static final Logger LOG = LoggerFactory.getLogger(StaffService.class);
    /** Демо-сотрудники стенда: по одному на роль (имена — как в realm Keycloak). */
    private static final List<StaffUser> DEMO = List.of(
            demoUser("Анна Смирнова", "specialist@demo.subsidy.local", StaffRole.SPECIALIST),
            demoUser("Игорь Волков", "senior@demo.subsidy.local", StaffRole.SENIOR_SPECIALIST),
            demoUser("Мария Лебедева", "auditor@demo.subsidy.local", StaffRole.AUDITOR));

    private final StaffRepository staff;
    private final SessionRepository sessions;
    private final StaffMailer mailer;
    private final StaffSettings settings;
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param staff    сотрудники
     * @param sessions сессии
     * @param mailer   письма
     * @param settings настройки
     * @param clock    часы
     */
    public StaffService(StaffRepository staff, SessionRepository sessions, StaffMailer mailer, StaffSettings settings, Clock clock) {
        this.staff = staff;
        this.sessions = sessions;
        this.mailer = mailer;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Все сотрудники.
     *
     * @return список
     */
    @Transactional(readOnly = true)
    public List<StaffUser> list() {
        return staff.list();
    }

    /**
     * Сотрудник по id.
     *
     * @param id идентификатор
     * @return сотрудник
     * @throws DomainException SESSION_EXPIRED — сотрудника нет или он отключён
     */
    @Transactional(readOnly = true)
    public StaffUser me(UUID id) {
        return staff.find(id).filter(user -> user.status() != StaffStatus.DISABLED)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_EXPIRED, "Сессия завершена — войдите снова"));
    }

    /**
     * Сотрудник по id.
     *
     * @param id идентификатор
     * @return сотрудник
     * @throws DomainException NOT_FOUND
     */
    @Transactional(readOnly = true)
    public StaffUser get(UUID id) {
        return staff.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, NOT_FOUND));
    }

    /**
     * Демо-сотрудники для быстрого входа — только на демо-стенде.
     *
     * @return список (на боевом стенде пустой)
     */
    @Transactional(readOnly = true)
    public List<StaffUser> demoUsers() {
        return settings.demo() ? staff.demo() : List.of();
    }

    /**
     * Приглашает сотрудника: статус INVITED и письмо (если письмо не ушло — приглашение всё равно создано).
     *
     * @param actorName кто приглашает
     * @param name      имя
     * @param email     почта
     * @param role      роль
     * @return сотрудник
     * @throws DomainException CONFLICT — почта уже занята
     */
    @Transactional
    public StaffUser invite(String actorName, String name, String email, StaffRole role) {
        StaffUser user = new StaffUser(UuidCreator.getTimeOrderedEpoch(), name.strip(), email.strip().toLowerCase(Locale.ROOT), role,
                StaffStatus.INVITED, false, null);
        if (!staff.insert(user, clock.instant())) {
            throw new DomainException(ErrorCode.CONFLICT, "Сотрудник с такой почтой уже есть");
        }
        mailer.invitation(user.email(), user.name(), LABELS.get(role), actorName == null ? SENIOR : actorName);
        return user;
    }

    /**
     * Меняет роль; новая роль попадёт в токен при ближайшем продлении.
     *
     * @param id   сотрудник
     * @param role роль
     * @return сотрудник
     * @throws DomainException NOT_FOUND, STAFF_LAST_SENIOR
     */
    @Transactional
    public StaffUser setRole(UUID id, StaffRole role) {
        staff.lockSeniors();
        StaffUser user = locked(id);
        if (user.role() == role) {
            return user;
        }
        if (user.role() == StaffRole.SENIOR_SPECIALIST) {
            guardLastSenior(user);
        }
        staff.setRole(id, role);
        return user.withRole(role);
    }

    /**
     * Отключает или включает сотрудника. Отключение закрывает все его сессии.
     *
     * @param id     сотрудник
     * @param active включить
     * @return сотрудник
     * @throws DomainException NOT_FOUND, STAFF_LAST_SENIOR
     */
    @Transactional
    public StaffUser setActive(UUID id, boolean active) {
        staff.lockSeniors();
        StaffUser user = locked(id);
        Instant now = clock.instant();
        StaffStatus status;
        if (active) {
            if (user.status() != StaffStatus.DISABLED) {
                return user;
            }
            status = user.lastSeen() == null ? StaffStatus.INVITED : StaffStatus.ACTIVE;
        } else {
            if (user.status() == StaffStatus.DISABLED) {
                return user;
            }
            if (user.role() == StaffRole.SENIOR_SPECIALIST) {
                guardLastSenior(user);
            }
            status = StaffStatus.DISABLED;
            sessions.revokeAll(id, now);
        }
        staff.setStatus(id, status);
        return user.withStatus(status);
    }

    /**
     * Старт: на демо-стенде — демо-сотрудники по одному на роль (на боевом они отключаются); первый старший специалист из
     * {@code STAFF_BOOTSTRAP_EMAIL}, если настоящих сотрудников ещё нет (иначе войти некому).
     */
    @Transactional
    public void bootstrap() {
        staff.lockSeniors();
        Instant now = clock.instant();
        if (settings.demo()) {
            DEMO.forEach(user -> staff.insert(user, now));
        } else {
            // боевой режим: демо-сотрудники, заведённые раньше на демо-стенде, больше не входят
            staff.disableDemo();
        }
        String email = settings.bootstrap();
        if (email != null && !email.isBlank() && !staff.anyReal()) {
            StaffUser senior = new StaffUser(UuidCreator.getTimeOrderedEpoch(), SENIOR,
                    email.strip().toLowerCase(Locale.ROOT), StaffRole.SENIOR_SPECIALIST, StaffStatus.INVITED, false, null);
            staff.insert(senior, now);
            LOG.info("Создан первый старший специалист {} — вход по коду из письма", senior.id());
        }
    }

    private StaffUser locked(UUID id) {
        return staff.lock(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, NOT_FOUND));
    }

    private void guardLastSenior(StaffUser user) {
        if (user.status() != StaffStatus.DISABLED && staff.activeSeniors() <= 1) {
            throw new DomainException(ErrorCode.STAFF_LAST_SENIOR, "Это последний старший специалист — сначала назначьте другого");
        }
    }

    private static StaffUser demoUser(String name, String email, StaffRole role) {
        return new StaffUser(UUID.nameUUIDFromBytes(("demo-staff:" + email).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name,
                email, role, StaffStatus.ACTIVE, true, null);
    }
}
