package ru.subsidy.package_.applicant;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.common.event.Topics;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.messaging.outbox.OutboxWriter;
import ru.subsidy.package_.organization.OrganizationService;
import ru.subsidy.package_.pkg.PackageService;

/**
 * Доступ к заявке по ключу: создание, возобновление по коду, проверка {@code Authorization: Applicant <key>}.
 */
@Service
public class ApplicantAccess {

    /** Автор действий заявителя в истории. */
    public static final String APPLICANT = "applicant";
    private static final String SCHEME = "Applicant ";
    private static final String DELETED = "DELETED";
    private static final String NOT_FOUND = "Заявка не найдена";
    private static final int MAX_RESUME_FAILURES = 5;
    private static final int PHONE_DIGITS = 11;

    private final DSLContext dsl;
    private final Clock clock;
    private final ApplicantKeys keys;
    private final OrganizationService organizations;
    private final OutboxWriter outbox;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     * @param keys ключи
     * @param organizations организации
     * @param outbox outbox
     */
    public ApplicantAccess(DSLContext dsl, Clock clock, ApplicantKeys keys, OrganizationService organizations,
                           OutboxWriter outbox) {
        this.dsl = dsl;
        this.clock = clock;
        this.keys = keys;
        this.organizations = organizations;
        this.outbox = outbox;
    }

    /**
     * Привязывает организацию к черновику по ИНН из первой анкеты и публикует PackageCreated (в транзакции вызова).
     *
     * @param applicationId заявка
     * @param inn ИНН из анкеты
     * @param programCode программа
     */
    public void attachOrganization(UUID applicationId, String inn, String programCode) {
        var org = organizations.getOrCreate(inn, APPLICANT);
        dsl.update(Tables.PACKAGES).set(Tables.PACKAGES.ORG_ID, org.id())
                .where(Tables.PACKAGES.ID.eq(applicationId)).execute();
        outbox.append(Topics.PACKAGE_PACKAGE_V1, "PackageCreated", applicationId.toString(),
                Map.of("packageId", applicationId.toString(), "orgId", org.id().toString(), "inn", org.inn(),
                        "programCode", programCode, "createdBy", APPLICANT));
    }

    /**
     * Новая заявка (черновик без организации — ИНН заявитель укажет в анкете).
     *
     * @param programCode программа (проверена вызывающим)
     * @param email почта
     * @param phone телефон или null
     * @param idempotencyKey ключ идемпотентности или null
     * @return доступ (ключ и код показываются один раз)
     */
    @Transactional
    public Access create(String programCode, String email, String phone, String idempotencyKey) {
        String idempotency = idempotencyKey == null ? null : APPLICANT + ":" + idempotencyKey;
        if (idempotency != null) {
            UUID existing = dsl.select(Tables.PACKAGES.ID).from(Tables.PACKAGES)
                    .where(Tables.PACKAGES.IDEMPOTENCY_KEY.eq(idempotency)).fetchOne(Tables.PACKAGES.ID);
            if (existing != null) {
                return rotate(existing);
            }
        }
        UUID id = UuidCreator.getTimeOrderedEpoch();
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(Tables.PACKAGES)
                .set(Tables.PACKAGES.ID, id)
                .set(Tables.PACKAGES.PROGRAM_CODE, programCode)
                .set(Tables.PACKAGES.STATUS, PackageService.COLLECTING)
                .set(Tables.PACKAGES.IDEMPOTENCY_KEY, idempotency)
                .set(Tables.PACKAGES.CREATED_BY, APPLICANT)
                .set(Tables.PACKAGES.CREATED_AT, now)
                .set(Tables.PACKAGES.UPDATED_AT, now)
                .execute();
        dsl.insertInto(Tables.APPLICANTS)
                .set(Tables.APPLICANTS.PACKAGE_ID, id)
                .set(Tables.APPLICANTS.EMAIL, email.strip().toLowerCase(Locale.ROOT))
                .set(Tables.APPLICANTS.PHONE, phone)
                .set(Tables.APPLICANTS.STATE, ApplicantViews.DRAFT)
                .set(Tables.APPLICANTS.CONSENT_AT, now)
                .set(Tables.APPLICANTS.CREATED_AT, now)
                .execute();
        return rotate(id);
    }

    /**
     * Возобновление по почте или телефону и коду: выдаёт новый ключ, старый перестаёт работать.
     *
     * @param phoneOrEmail почта или телефон
     * @param code код возобновления
     * @return доступ
     * @throws DomainException APPLICATION_NOT_FOUND, RATE_LIMITED
     */
    @Transactional(noRollbackFor = DomainException.class)
    public Access resume(String phoneOrEmail, String code) {
        String contact = phoneOrEmail.strip();
        var match = contact.contains("@")
                ? Tables.APPLICANTS.EMAIL.eq(contact.toLowerCase(Locale.ROOT)) : Tables.APPLICANTS.PHONE.eq(phone(contact));
        var rows = dsl.select(Tables.APPLICANTS.PACKAGE_ID, Tables.APPLICANTS.RESUME_HASH,
                        Tables.APPLICANTS.RESUME_FAILURES).from(Tables.APPLICANTS)
                .where(match).and(Tables.APPLICANTS.STATE.ne(DELETED)).fetch();
        for (var row : rows) {
            UUID id = row.get(Tables.APPLICANTS.PACKAGE_ID);
            if (row.get(Tables.APPLICANTS.RESUME_FAILURES) >= MAX_RESUME_FAILURES) {
                throw new DomainException(ErrorCode.RATE_LIMITED, "Слишком много попыток — обратитесь в агентство");
            }
            if (keys.resumeHash(id, code).equals(row.get(Tables.APPLICANTS.RESUME_HASH))) {
                return rotate(id);
            }
        }
        dsl.update(Tables.APPLICANTS).set(Tables.APPLICANTS.RESUME_FAILURES, Tables.APPLICANTS.RESUME_FAILURES.plus(1))
                .where(match).and(Tables.APPLICANTS.STATE.ne(DELETED)).execute();
        throw notFound();
    }

    /**
     * Проверяет ключ заявки.
     *
     * @param applicationId заявка из пути
     * @param authorization заголовок Authorization
     * @throws DomainException APPLICATION_NOT_FOUND — чужая заявка, неверный или отозванный ключ
     */
    public void authorize(UUID applicationId, String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            throw notFound();
        }
        String hash = keys.keyHash(authorization.substring(SCHEME.length()).strip());
        boolean valid = dsl.fetchExists(Tables.APPLICANTS, Tables.APPLICANTS.PACKAGE_ID.eq(applicationId)
                .and(Tables.APPLICANTS.KEY_HASH.eq(hash)).and(Tables.APPLICANTS.STATE.ne(DELETED)));
        if (!valid) {
            throw notFound();
        }
    }

    /**
     * Отзывает доступ и контакты (удаление заявки).
     *
     * @param applicationId заявка
     */
    public void revoke(UUID applicationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.update(Tables.APPLICANTS)
                .set(Tables.APPLICANTS.STATE, DELETED)
                .set(Tables.APPLICANTS.KEY_HASH, (String) null)
                .set(Tables.APPLICANTS.RESUME_HASH, (String) null)
                .set(Tables.APPLICANTS.EMAIL, (String) null)
                .set(Tables.APPLICANTS.PHONE, (String) null)
                .set(Tables.APPLICANTS.DELETED_AT, now)
                .where(Tables.APPLICANTS.PACKAGE_ID.eq(applicationId)).execute();
    }

    /**
     * Ошибка «заявка не найдена» (одинаковая для чужой и несуществующей).
     *
     * @return исключение
     */
    public static DomainException notFound() {
        return new DomainException(ErrorCode.APPLICATION_NOT_FOUND, NOT_FOUND);
    }

    /**
     * Телефон в формате +7…: только цифры, 8 → 7.
     *
     * @param raw ввод
     * @return нормализованный номер
     */
    public static String phone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == PHONE_DIGITS && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        return "+" + digits;
    }

    private Access rotate(UUID id) {
        String key = keys.newKey();
        String code = keys.newResumeCode();
        dsl.update(Tables.APPLICANTS)
                .set(Tables.APPLICANTS.KEY_HASH, keys.keyHash(key))
                .set(Tables.APPLICANTS.RESUME_HASH, keys.resumeHash(id, code))
                .set(Tables.APPLICANTS.RESUME_FAILURES, 0)
                .where(Tables.APPLICANTS.PACKAGE_ID.eq(id)).execute();
        return new Access(id, key, code, "/apply/" + id + "#k=" + key);
    }

    /**
     * Доступ к заявке.
     *
     * @param applicationId заявка
     * @param accessKey ключ (показывается один раз)
     * @param resumeCode код возобновления (показывается один раз)
     * @param link относительная ссылка на кабинет
     */
    public record Access(UUID applicationId, String accessKey, String resumeCode, String link) {
    }
}
