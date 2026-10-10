package ru.subsidy.package_.staff;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.jooq.tables.StaffLoginCodes;

/**
 * Коды входа: один активный на адрес.
 */
@Repository
public class LoginCodeRepository {

    private static final StaffLoginCodes CODES = Tables.STAFF_LOGIN_CODES;

    private final DSLContext dsl;

    public LoginCodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Код адреса, строка заблокирована.
     *
     * @param email почта в нижнем регистре
     * @return код
     */
    public Optional<CodeRow> lock(String email) {
        return dsl.selectFrom(CODES).where(CODES.EMAIL.eq(email)).forUpdate()
                .fetchOptional(row -> new CodeRow(row.getCodeHash(), row.getSalt(), row.getAttempts(), row.getExpiresAt().toInstant(),
                        row.getResendAt().toInstant()));
    }

    /**
     * Сохраняет новый код, заменяя прежний (попытки обнуляются).
     *
     * @param email     почта в нижнем регистре
     * @param codeHash  HMAC кода или {@code null} для неизвестного адреса
     * @param salt      соль
     * @param expiresAt действует до
     * @param resendAt  повторно не раньше
     */
    public void put(String email, String codeHash, String salt, Instant expiresAt, Instant resendAt) {
        dsl.insertInto(CODES)
                .set(CODES.EMAIL, email)
                .set(CODES.CODE_HASH, codeHash)
                .set(CODES.SALT, salt)
                .set(CODES.ATTEMPTS, 0)
                .set(CODES.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .set(CODES.RESEND_AT, resendAt.atOffset(ZoneOffset.UTC))
                .onConflict(CODES.EMAIL).doUpdate()
                .set(CODES.CODE_HASH, codeHash)
                .set(CODES.SALT, salt)
                .set(CODES.ATTEMPTS, 0)
                .set(CODES.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .set(CODES.RESEND_AT, resendAt.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Неверная попытка.
     *
     * @param email почта
     * @return попыток после увеличения
     */
    public int failed(String email) {
        return dsl.update(CODES).set(CODES.ATTEMPTS, CODES.ATTEMPTS.plus(1)).where(CODES.EMAIL.eq(email))
                .returning(CODES.ATTEMPTS).fetchOne(CODES.ATTEMPTS);
    }

    /**
     * Гасит код.
     *
     * @param email почта
     */
    public void delete(String email) {
        dsl.deleteFrom(CODES).where(CODES.EMAIL.eq(email)).execute();
    }

    /**
     * Строка кода.
     *
     * @param codeHash  HMAC или {@code null}
     * @param salt      соль
     * @param attempts  неверных попыток
     * @param expiresAt действует до
     * @param resendAt  повторно не раньше
     */
    public record CodeRow(String codeHash, String salt, int attempts, Instant expiresAt, Instant resendAt) {
    }
}
